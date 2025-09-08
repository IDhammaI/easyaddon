package me.idhammai.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

public class NetherElytraPath extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private Progress currentProgress; // 当前进度
    private ReplenishProgress replenishProgress; // 当前的补货进度
    private int timer = 0; // 延迟刻数
    private final BlockPos.Mutable bp = new BlockPos.Mutable(); // 放置的潜影盒位置
    boolean shouldLandForElytra = false;
    boolean shouldLandForFirework = false;

    // 目标坐标设置
    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
            .name("目标X坐标")
            .description("目标X坐标")
            .defaultValue(0)
            .range(-30000000, 30000000)
            .sliderRange(-30000000, 30000000)
            .build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
            .name("目标Z坐标")
            .description("目标Z坐标")
            .defaultValue(0)
            .range(-30000000, 30000000)
            .sliderRange(-30000000, 30000000)
            .build());

    public NetherElytraPath() {
        super(EasyAddon.CATEGORY, "nether-elytra-path", "地狱鞘翅自动寻路飞行辅助");
    }

    @Override
    public void onActivate() {
        if (mc.player == null)
            return;

        currentProgress = Progress.Pathing;
        resetReplenishState();

        // 设置Baritone API参数（xin服最佳参数）
        Settings settings = BaritoneAPI.getSettings();
        settings.elytraTermsAccepted.value = true;
        settings.elytraNetherSeed.value = 3763250021837776656L;
        settings.elytraPredictTerrain.value = true;
        settings.elytraAutoJump.value = true;

        // 屏蔽自动降落功能，由当前功能来控制
        settings.elytraMinFireworksBeforeLanding.value = -1;
        settings.elytraMinimumDurability.value = -1;

        // 执行Baritone命令
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager()
                .execute("goal " + targetX.get() + " ~ " + targetZ.get());
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
    }

    @Override
    public void onDeactivate() {
        // 取消Baritone寻路
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        resetReplenishState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null)
            return;

        switch (currentProgress) {
            case Pathing -> handlePathing();
            case Landing -> handleLanding();
            case Replenishing -> handleReplenishing();
            case IDLE -> {}
        }
    }

    private void handlePathing() {
        // 检查鞘翅数量和耐久度
        int elytraCount = InvUtils.find(Items.ELYTRA).count();
        if (elytraCount == 1) {
            FindItemResult elytraResult = InvUtils.find(Items.ELYTRA);
            if (elytraResult.found()) {
                ItemStack elytraStack = mc.player.getInventory().getStack(elytraResult.slot());
                int durability = elytraStack.getMaxDamage() - elytraStack.getDamage();
                if (durability < 10) {
                    shouldLandForElytra = true;
                    ChatUtils.info("鞘翅耐久度不足，准备降落...");
                }
            }
        }

        // 检查烟花数量
        int fireworkCount = InvUtils.find(Items.FIREWORK_ROCKET).count();
        if (fireworkCount <= 5) {
            shouldLandForFirework = true;
            ChatUtils.info("烟花数量不足，准备降落...");
        }

        // 判断是否要降落
        if (shouldLandForElytra || shouldLandForFirework) {
            int currentX = mc.player.getBlockX();
            int currentZ = mc.player.getBlockZ();

            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager()
                    .execute("goal " + currentX + " ~ " + currentZ);

            currentProgress = Progress.Landing;
        }

    }

    private void handleLanding() {
        // 检查Baritone是否完成降落
        boolean isPathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        boolean isOnGround = mc.player.isOnGround();
        boolean isFallFlying = mc.player.isFallFlying();

        if (!isPathing && isOnGround && !isFallFlying) {
            currentProgress = Progress.Replenishing;
            replenishProgress = ReplenishProgress.TAKE_SHULKER;
            timer = 0;
            ChatUtils.info("降落完成，开始补充物品流程...");
        }
    }

    private void handleReplenishing() {
        // 每个步骤都有延迟，确保动作能够完成
        if (timer > 0) {
            timer--;
            return;
        }

        switch (replenishProgress) {
            case TAKE_SHULKER -> {
                if (findShulkerWithFireworks()) {
                    replenishProgress = ReplenishProgress.PLACE_ON_GROUND;
                    timer = 10;
                    ChatUtils.info("找到含烟花的潜影盒，切换到手上...");
                } else {
                    ChatUtils.error("未找到含烟花的潜影盒！");
                }
            }
            case PLACE_ON_GROUND -> {
                if (placeShulkerOnGround()) {
                    replenishProgress = ReplenishProgress.OPEN_SHULKER;
                    timer = 10;
                    ChatUtils.info("潜影盒已放置在地面，准备下一个...");
                } else {
                    ChatUtils.error("无法放置潜影盒在地面！");
                }
            }
            case OPEN_SHULKER -> {
                if (openShulkerBox()) {
                    replenishProgress = ReplenishProgress.TAKE_FIREWORKS;
                    timer = 10;
                    ChatUtils.info("潜影盒已打开，准备下一个...");
                }
            }
            case TAKE_FIREWORKS -> {
                if (takeFireworks()) {
                    currentProgress = Progress.IDLE;
                    timer = 10;
                    ChatUtils.info("已成功拿取烟花，准备下一个...");
                } else {
                    ChatUtils.error("无法拿取烟花！");
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    private void resetReplenishState() {
        replenishProgress = ReplenishProgress.TAKE_SHULKER;
        timer = 0;
    }

    private boolean findShulkerWithFireworks() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            // 提前返回：不是方块物品就跳过
            if (!(stack.getItem() instanceof net.minecraft.item.BlockItem blockItem)) {
                continue;
            }

            // 提前返回：不是潜影盒就跳过
            if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
                continue;
            }

            // 检查是否全部都是烟花
            if (isShulkerFullOfFireworks(stack)) {
                InvUtils.move().from(i).toHotbar(mc.player.getInventory().selectedSlot);
                return true;
            }
        }
        return false;
    }

    private boolean isShulkerFullOfFireworks(ItemStack shulkerStack) {
        // 没有NBT数据就返回false
        if (!shulkerStack.hasNbt() || !shulkerStack.getNbt().contains("BlockEntityTag")) {
            return false;
        }

        var blockEntityTag = shulkerStack.getNbt().getCompound("BlockEntityTag");
        if (!blockEntityTag.contains("Items")) {
            return false;
        }

        var itemsList = blockEntityTag.getList("Items", 10);

        // 空潜影盒返回false
        if (itemsList.size() == 0) {
            return false;
        }

        // 检查每个物品是否都是烟花
        for (int i = 0; i < itemsList.size(); i++) {
            var itemCompound = itemsList.getCompound(i);
            String itemId = itemCompound.getString("id");

            if (!"minecraft:firework_rocket".equals(itemId)) {
                return false;
            }
        }

        return true;
    }

    private boolean placeShulkerOnGround() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        mc.player.jump();
        Vec3d vec = mc.player.getPos().add(mc.player.getVelocity()).add(0, -0.75, 0);
        Vec3d pos = mc.player.getPos();
        bp.set(pos.x, vec.y, pos.z);

        // 放置潜影盒
        if (place(bp)) {
            return true;
        }

        return false;
    }

    private boolean place(BlockPos bp) {
        // 查找潜影盒物品
        FindItemResult shulkerItem = InvUtils.findInHotbar(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock() instanceof ShulkerBoxBlock;
            }
            return false;
        });

        // 如果没有找到潜影盒，返回false
        if (!shulkerItem.found()) {
            ChatUtils.error("没有找到潜影盒！");
            return false;
        }

        // 放置潜影盒
        if (BlockUtils.place(bp, shulkerItem, true, 50, true, true)) {
            ChatUtils.info("潜影盒放置成功！");
            return true;
        }
        return false;
    }

    private boolean openShulkerBox() {
        // 打开玩家脚下的潜影盒 bp 位置
        if (mc.player == null || mc.world == null) {
            return false;
        }

        // 检查 bp 位置是否有效
        if (bp == null) {
            ChatUtils.error("潜影盒位置无效！");
            return false;
        }

        // 检查该位置是否确实是潜影盒
        BlockState blockState = mc.world.getBlockState(bp);
        if (!(blockState.getBlock() instanceof ShulkerBoxBlock)) {
            ChatUtils.error("该位置不是潜影盒！");
            return false;
        }

        // 使用 Rotations API 旋转视线到目标方块
        Rotations.rotate(Rotations.getYaw(bp), Rotations.getPitch(bp), () -> {
            // 获取方块命中结果，用于交互
            BlockHitResult hitResult = new BlockHitResult(
                    Vec3d.ofCenter(bp), // 点击潜影盒中心
                    Direction.UP, // 从上方点击
                    bp, // 目标方块位置
                    false // 不是内部点击
            );

            // 模拟右键单击，使用网络包发送方式打开潜影盒
            int sequence = mc.player.getInventory().selectedSlot; // 使用玩家当前选择的槽位作为序列
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence));
        });

        return true;
    }

    private boolean takeFireworks() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        return true;
    }

    // ==================== 枚举定义 ====================

    private enum Progress {
        Pathing, // 寻路
        Landing, // 降落
        Replenishing, // 补充物品
        IDLE, // 等待
    }

    private enum ReplenishProgress {
        TAKE_SHULKER, // 把含烟花的潜影盒拿到手中
        PLACE_ON_GROUND, // 跳一下，放到自己的脚下
        OPEN_SHULKER, // 打开潜影盒
        TAKE_FIREWORKS, // 拿取烟花
        BREAK_SHULKER, // 挖掉潜影盒
    }
}
