package com.example.charmobmaker.smoke;

import com.example.charmobmaker.CharMobMaker;
import com.example.charmobmaker.DesignStore;
import com.example.charmobmaker.Designs;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.item.MobEggItem;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.DesignJson;
import com.example.charmobmaker.model.DesignRules;
import com.example.charmobmaker.model.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

// Real headless server: gradlew runGameTestServer -Psmoke. Mobs are spawned and left to get on with it.
@GameTestHolder(CharMobMaker.MODID)
@PrefixGameTestTemplate(false)
public final class SmokeTests {
    private SmokeTests() {}

    private static CustomMob spawn(GameTestHelper h, String id, int x, int z) {
        BlockPos at = h.absolutePos(new BlockPos(x, 2, z));
        CustomMob m = CustomMob.spawnAt(h.getLevel(), at, id);
        if (m == null) throw new IllegalStateException("couldn't spawn " + id);
        return m;
    }

    // A made-up design just for one test, stored the same way an upload is.
    private static String design(String name, java.util.function.Consumer<Stats> stats) {
        Design d = new Design();
        d.name = name;
        stats.accept(d.stats);
        DesignStore.Saved saved = DesignStore.store(d, "", "test");
        if (!saved.ok()) throw new IllegalStateException(saved.message());
        return saved.id();
    }

    @GameTest(template = "empty")
    public static void startersAreThere(GameTestHelper h) {
        h.succeedWhen(() -> {
            for (String id : new String[] {"pink_cat", "inky", "block_golem", "chicken_headed_cow", "bone_knight", "magma_eye"}) {
                h.assertTrue(Designs.SERVER.containsKey(id), "missing starter " + id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void statsComeFromTheDesign(GameTestHelper h) {
        CustomMob golem = spawn(h, "block_golem", 2, 2);
        h.assertTrue(Math.abs(golem.getMaxHealth() - 60f) < 0.01f, "health " + golem.getMaxHealth());
        h.assertTrue(Math.abs(golem.getHealth() - 60f) < 0.01f, "current health " + golem.getHealth());
        h.assertTrue(Math.abs(golem.getBbHeight() - 2.8f) < 0.01f, "height " + golem.getBbHeight());
        h.assertTrue(Math.abs(golem.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7.0) < 0.01, "damage");
        h.assertTrue(golem.isHostile(), "the golem should be hostile");
        h.assertTrue(golem.getName().getString().equals("Block Golem"), "name " + golem.getName().getString());
        CustomMob eye = spawn(h, "magma_eye", 4, 2);
        h.assertTrue(eye.isNoGravity(), "flying mob should have no gravity");
        h.assertTrue(eye.getNavigation() instanceof FlyingPathNavigation, "flying mob should path through the air");
        h.assertTrue(eye.fireImmune(), "the eye should be fire immune");
        CustomMob cow = spawn(h, "chicken_headed_cow", 6, 2);
        h.assertFalse(cow.isHostile(), "the cow shouldn't be hostile");
        h.assertFalse(cow.getNavigation() instanceof FlyingPathNavigation, "the cow walks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hostileMobHuntsItsTarget(GameTestHelper h) {
        String id = design("Pig Hunter", s -> {
            s.mood = Stats.Mood.HOSTILE;
            s.targets.clear();
            s.targets.add("minecraft:pig");
            s.damage = 200f;
            s.speed = 0.4f;
            s.attackRate = 0.25f;
        });
        CustomMob hunter = spawn(h, id, 1, 1);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(8, 2, 8));
        pig.setNoAi(true);
        h.succeedWhen(() -> h.assertFalse(pig.isAlive(), "the pig is still alive, hunter target " + hunter.getTarget()));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void passiveMobLeavesThingsAlone(GameTestHelper h) {
        String id = design("Gentle", s -> {
            s.mood = Stats.Mood.PASSIVE;
            s.targets.clear();
            s.targets.add("minecraft:pig");
        });
        CustomMob m = spawn(h, id, 2, 2);
        h.spawn(EntityType.PIG, new BlockPos(5, 2, 5));
        h.runAfterDelay(100, () -> {
            h.assertTrue(m.getTarget() == null, "a passive mob got a target");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void arrowsGetShot(GameTestHelper h) {
        String id = design("Archer", s -> {
            s.mood = Stats.Mood.HOSTILE;
            s.attack = Stats.Attack.ARROW;
            s.targets.clear();
            s.targets.add("minecraft:pig");
            s.attackRate = 0.5f;
        });
        CustomMob m = spawn(h, id, 1, 1);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 1));
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        h.succeedWhen(() -> h.assertTrue(!h.getLevel().getEntitiesOfClass(AbstractArrow.class, m.getBoundingBox().inflate(16.0)).isEmpty(), "no arrow yet"));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fireballsGetShot(GameTestHelper h) {
        String id = design("Fire Thrower", s -> {
            s.mood = Stats.Mood.HOSTILE;
            s.attack = Stats.Attack.FIREBALL;
            s.targets.clear();
            s.targets.add("minecraft:pig");
            s.attackRate = 0.5f;
        });
        CustomMob m = spawn(h, id, 1, 1);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 1));
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        h.succeedWhen(() -> h.assertTrue(!h.getLevel().getEntitiesOfClass(SmallFireball.class, m.getBoundingBox().inflate(16.0)).isEmpty(), "no fireball yet"));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dropsAndDeathClipLength(GameTestHelper h) {
        CustomMob m = spawn(h, "block_golem", 3, 3);
        m.setNoAi(true);
        BlockPos at = m.blockPosition();
        m.kill();
        h.runAfterDelay(25, () -> h.assertTrue(!m.isRemoved(), "its 2 second death clip should still be playing after 25 ticks"));
        h.succeedWhen(() -> {
            h.assertTrue(m.isRemoved(), "not gone yet");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(4.0)).isEmpty(), "no drops");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void flyerGoesUp(GameTestHelper h) {
        CustomMob eye = spawn(h, "magma_eye", 3, 3);
        double y = eye.getY();
        eye.getNavigation().moveTo(eye.getX(), eye.getY() + 4.0, eye.getZ(), 1.0);
        h.succeedWhen(() -> h.assertTrue(eye.getY() > y + 1.0, "it should have flown up"));
    }

    @GameTest(template = "empty")
    public static void changingADesignChangesLiveMobs(GameTestHelper h) {
        String id = design("Changing", s -> s.health = 10f);
        CustomMob m = spawn(h, id, 2, 2);
        h.assertTrue(Math.abs(m.getMaxHealth() - 10f) < 0.01f, "start health");
        Design d = Designs.SERVER.get(id).copy();
        d.stats.health = 50f;
        d.stats.width = 2f;
        DesignStore.store(d, "", "test");
        h.assertTrue(Math.abs(m.getMaxHealth() - 50f) < 0.01f, "new health " + m.getMaxHealth());
        h.assertTrue(Math.abs(m.getBbWidth() - 2f) < 0.01f, "new width " + m.getBbWidth());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nastyUploadsCantEscapeOrBreakIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Design evil = new Design();
        evil.name = "../../../evil\\file";
        evil.id = "../../../evil";
        evil.stats.health = 1e9f;
        DesignStore.Saved saved = DesignStore.store(evil, "", "test");
        h.assertTrue(saved.ok(), saved.message());
        h.assertTrue(saved.id().matches("[a-z0-9_]+"), "id " + saved.id());
        Path folder = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("charmobmaker").resolve("designs");
        h.assertTrue(Files.exists(folder.resolve(saved.id() + ".json")), "the file should be in the designs folder");
        h.assertFalse(Files.exists(folder.getParent().getParent().getParent().resolve("evil.json")), "no file outside");
        h.assertTrue(Designs.SERVER.get(saved.id()).stats.health <= 1000f, "health should be clamped");
        h.assertTrue(DesignStore.delete(saved.id()), "delete");
        h.assertFalse(DesignStore.delete("../../level"), "delete can't reach out");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyStarterIsTidyAlready(GameTestHelper h) throws Exception {
        for (Design d : Designs.SERVER.values()) {
            String before = DesignJson.write(d);
            Design again = DesignJson.read(before);
            DesignRules.tidy(again);
            again.id = d.id;
            h.assertTrue(before.equals(DesignJson.write(again)), "design " + d.id + " changed when tidied again");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eggHatchesAndBenchCanBeCrafted(GameTestHelper h) {
        var player = h.makeMockPlayer();
        ItemStack egg = MobEggItem.of(Designs.SERVER.get("pink_cat"));
        egg.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        BlockPos floor = h.absolutePos(new BlockPos(4, 1, 4));
        var result = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        h.assertTrue(result.consumesAction(), "the egg didn't do anything: " + result);
        h.assertTrue(egg.getCount() == 1, "the egg should get used up");
        var mobs = h.getLevel().getEntitiesOfClass(CustomMob.class, new net.minecraft.world.phys.AABB(floor).inflate(2.0), m -> "pink_cat".equals(m.designId()));
        h.assertTrue(!mobs.isEmpty(), "no cat hatched");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation(CharMobMaker.MODID, "mob_makers_bench")).isPresent(), "the bench recipe is missing");
        h.succeed();
    }
}
