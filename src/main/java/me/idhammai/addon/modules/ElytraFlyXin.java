package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import me.idhammai.addon.events.impl.MoveEvent;
import me.idhammai.addon.events.impl.TravelEvent;
import net.minecraft.client.MinecraftClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ElytraFlyXin extends Module {
    static MinecraftClient mc = MinecraftClient.getInstance();

    public ElytraFlyXin() {
        super(EasyAddon.CATEGORY, "elytrafly-xin", "Xin专用飞行");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> autoStop = sgGeneral.add(new BoolSetting.Builder()
        .name("未加载区块时停止")
        .description("当区块没加载时停止飞行")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("飞行速度")
        .description("飞行的水平速度")
        .defaultValue(1.5)
        .min(0.1)
        .sliderMin(0.1)
        .max(3)
        .sliderMax(3)
        .build()
    );

    public final Setting<Double> downSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("下降速度")
        .description("下降时的速度")
        .defaultValue(1)
        .min(0.1)
        .sliderMin(0.1)
        .max(3)
        .sliderMax(3)
        .build()
    );

    // 标记玩家是否装备了鞘翅
    private boolean hasElytra = false;


    @Override
    public void onActivate() {
        if (mc.player != null) {
            // 如果不是创造模式，禁用原版飞行能力
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        hasElytra = false;
    }

    @Override
    public void onDeactivate() {
        hasElytra = false;
        if (mc.player != null) {
            // 如果不是创造模式，禁用原版飞行能力
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // 遍历玩家的装备栏，检查是否有鞘翅
        for (ItemStack is : mc.player.getArmorItems()) {
            if (is.getItem() instanceof ElytraItem) {
                hasElytra = true;
                break;
            } else {
                hasElytra = false;
            }
        }
    }

    /**
     * 根据俯仰角和偏航角计算旋转向量
     * 这是3D空间中方向计算的核心方法
     *
     * @param pitch 俯仰角（上下角度）
     * @param yaw   偏航角（左右角度）
     * @return 返回标准化的3D方向向量
     */
    protected final Vec3d getRotationVector(float pitch, float yaw) {
        // 将角度转换为弧度（乘以π/180的近似值）
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;

        // 计算三角函数值
        float h = MathHelper.cos(g);  // cos(yaw)
        float i = MathHelper.sin(g);  // sin(yaw)
        float j = MathHelper.cos(f);  // cos(pitch)
        float k = MathHelper.sin(f);  // sin(pitch)

        // 返回3D方向向量 (x, y, z)
        return new Vec3d(i * j, -k, h * j);
    }

    /**
     * 获取当前的旋转向量（水平方向）
     * 固定俯仰角为0，只考虑水平方向的旋转
     *
     * @param tickDelta 帧间插值
     * @return 返回水平方向的向量
     */
    public final Vec3d getRotationVec(float tickDelta) {
        // 俯仰角固定为0，实现水平飞行
        return this.getRotationVector(0, mc.player.getYaw(tickDelta));
    }

    /**
     * 重新启动鞘翅飞行
     * 向服务器发送开始鞘翅飞行的数据包
     *
     * @param player 玩家实体
     * @return 是否成功启动鞘翅飞行
     */
    public static boolean recastElytra(ClientPlayerEntity player) {
        // 检查条件并尝试启动鞘翅飞行
        if (checkConditions(player) && ignoreGround(player)) {
            // 发送开始鞘翅飞行的数据包给服务器
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        } else return false;
    }

    /**
     * 检查鞘翅飞行的基本条件
     *
     * @param player 玩家实体
     * @return 是否满足鞘翅飞行条件
     */
    public static boolean checkConditions(ClientPlayerEntity player) {
        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
        return (!player.getAbilities().flying &&     // 不在创造模式飞行
            !player.hasVehicle() &&              // 没有乘坐载具
            !player.isClimbing() &&              // 没有在攀爬
            itemStack.isOf(Items.ELYTRA) &&      // 装备了鞘翅
            ElytraItem.isUsable(itemStack));     // 鞘翅可用（有耐久度）
    }

    /**
     * 忽略地面检测，强制启动鞘翅飞行
     *
     * @param player 玩家实体
     * @return 是否成功启动
     */
    private static boolean ignoreGround(ClientPlayerEntity player) {
        // 检查玩家不在水中且没有漂浮效果
        if (!player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            // 验证鞘翅装备和可用性
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                // 强制开始鞘翅飞行
                player.startFallFlying();
                return true;
            } else return false;
        } else return false;
    }

    public static double[] directionSpeedKey(double speed) {
        float forward = (mc.options.forwardKey.isPressed() ? 1 : 0) + (mc.options.backKey.isPressed() ? -1 : 0);
        float side = (mc.options.leftKey.isPressed() ? 1 : 0) + (mc.options.rightKey.isPressed() ? -1 : 0);
        float yaw = mc.player.prevYaw + (mc.player.getYaw() - mc.player.prevYaw) * 1.0f;
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }

    @EventHandler
    public void onPlayerMove(MoveEvent event) {
        // 检查玩家是否装备了鞘翅
        if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem)) return;

        // 如果玩家正在鞘翅飞行
        if (mc.player.isFallFlying()) {
            // 计算玩家当前所在的区块坐标
            int chunkX = (int) ((mc.player.getX()) / 16);
            int chunkZ = (int) ((mc.player.getZ()) / 16);

            // 如果启用了自动停止功能
            if (autoStop.get()) {
                // 检查当前区块是否已加载
                if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    // 在未加载区块中停止所有移动，防止触发反作弊
                    event.setX(0);
                    event.setY(0);
                    event.setZ(0);
                }
            }
        }
    }


    @EventHandler
    public void onMove(TravelEvent event) {
        // 基础检查：空指针、是否有鞘翅、是否在飞行、是否为后处理事件
        if (mc.player == null || mc.world == null || !hasElytra || !mc.player.isFallFlying() || event.isPost()) return;

        // 获取玩家当前的朝向向量（水平方向）
        Vec3d lookVec = getRotationVec(1.0f);
        // 计算水平朝向距离（用于方向计算）
        double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        // 计算当前水平移动距离
        double motionDist = Math.sqrt(getX() * getX() + getZ() * getZ());

        // 处理垂直移动控制
        if (mc.player.input.sneaking) {
            // 潜行时下降，使用配置的下降速度
            setY(-downSpeed.get());
        } else if (!mc.player.input.jumping) {
            // 非跳跃状态时，Y轴速度设为接近0（保持水平飞行）
            setY(-0.00000000003D * 0);
        }

        // 处理跳跃时的上升逻辑
        if (mc.player.input.jumping) {
            // 如果有水平移动速度
            if (motionDist > 0 / 10) {
                // 基于当前移动速度计算上升速度
                double rawUpSpeed = motionDist * 0.01325D;
                // 应用上升速度
                setY(getY() + rawUpSpeed * 3.2D);
                // 调整水平速度以保持平衡
                setX(getX() - lookVec.x * rawUpSpeed / lookDist);
                setZ(getZ() - lookVec.z * rawUpSpeed / lookDist);
            } else {
                // 如果没有水平移动，直接应用方向速度
                double[] dir = directionSpeedKey(speed.get());
                setX(dir[0]);
                setZ(dir[1]);
            }
        }

        // 平滑的方向调整 - 让飞行更自然
        if (lookDist > 0.0D) {
            // 使用插值方法平滑调整飞行方向，避免突然的方向变化
            setX(getX() + (lookVec.x / lookDist * motionDist - getX()) * 0.1D);
            setZ(getZ() + (lookVec.z / lookDist * motionDist - getZ()) * 0.1D);
        }

        // 非跳跃状态下的水平移动控制
        if (!mc.player.input.jumping) {
            // 根据键盘输入和配置的速度设置水平移动
            double[] dir = directionSpeedKey(speed.get());
            setX(dir[0]);
            setZ(dir[1]);
        }

        // 应用空气阻力 - 模拟真实的飞行物理
        // 这些精确的数值是为了模拟原版的空气阻力效果，避免被反作弊检测
        setY(getY() * 0.9900000095367432D);  // Y轴阻力（1%衰减）
        setX(getX() * 0.9800000190734863D);  // X轴阻力（2%衰减）
        setZ(getZ() * 0.9900000095367432D);  // Z轴阻力（1%衰减）

        // 取消原始事件，使用我们自定义的移动逻辑
        event.cancel();
        // 应用计算后的移动
        mc.player.move(MovementType.SELF, mc.player.getVelocity());
    }


    /**
     * 获取X轴速度
     *
     * @return X轴移动速度
     */
    private double getX() {
        return mc.player.getVelocity().x;

    }

    /**
     * 获取Y轴速度
     *
     * @return Y轴移动速度
     */
    private double getY() {
        return mc.player.getVelocity().y;
    }

    /**
     * 获取Z轴速度
     *
     * @return Z轴移动速度
     */
    private double getZ() {
        return mc.player.getVelocity().z;
    }

    /**
     * 设置X轴速度
     *
     * @param f 新的X轴速度
     */
    private void setX(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        // 创建新的速度向量
        Vec3d newVel = new Vec3d(f, currentVel.y, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置Y轴速度
     *
     * @param f 新的Y轴速度
     */
    private void setY(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        // 创建新的速度向量
        Vec3d newVel = new Vec3d(currentVel.x, f, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置Z轴速度
     *
     * @param f 新的Z轴速度
     */
    private void setZ(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        // 创建新的速度向量
        Vec3d newVel = new Vec3d(currentVel.x, currentVel.y, f);
        mc.player.setVelocity(newVel);
    }
}
