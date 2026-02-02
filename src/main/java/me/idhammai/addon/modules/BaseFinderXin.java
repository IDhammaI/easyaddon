package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BaseFinderXin extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgRestart = settings.createGroup("Restart");
    private ChunkPos originChunk;
    private ChunkPos targetChunk;
    private int currentCircle;
    private PathEnum currentPath;
    private boolean isBack;
    private int turnDelayTimer;
    private float targetYaw;

    private boolean forcingForward;

    // 存储所有目标区块
    private final Set<ChunkPos> targetChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<ChunkPos> visitedChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<ChunkPos> currentPathChunks = Collections.synchronizedSet(new HashSet<>());

    // 设置玩家当前世界的加载的区块范围
    public final Setting<Integer> chunkLoadRadius = sgGeneral.add(new IntSetting.Builder()
            .name("区块加载范围")
            .description("当前世界加载的区块范围")
            .defaultValue(5)
            .min(2)
            .max(10)
            .sliderMin(2)
            .sliderMax(10)
            .build());

    // 设置搜索圈数
    public final Setting<Integer> circleLimit = sgGeneral.add(new IntSetting.Builder()
            .name("搜索圈数限制")
            .description("到达搜索圈数限制停止")
            .defaultValue(50)
            .min(2)
            .max(Integer.MAX_VALUE)
            .sliderMin(2)
            .sliderMax(100)
            .build());

    // 移动速度设置
    private final Setting<Double> moveSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("移动速度")
            .description("移动速度")
            .defaultValue(3.0)
            .min(0.01)
            .max(3.0)
            .sliderMin(0.01)
            .sliderMax(3.0)
            .build());

    // 转向延迟tick
    private final Setting<Integer> turnDelay = sgGeneral.add(new IntSetting.Builder()
            .name("转向延迟")
            .description("转向的延迟确保区块加载")
            .defaultValue(40)
            .min(1)
            .max(100)
            .sliderMin(1)
            .sliderMax(100)
            .build());

    // 修复卡顿
    private final Setting<Boolean> waitChunkLoad = sgGeneral.add(new BoolSetting.Builder()
            .name("等待区块加载")
            .description("等待区块加载确保搜索到的区块加载")
            .defaultValue(true)
            .build());

    // 是否从上次开始
    private final Setting<Boolean> lastBegin = sgRestart.add(new BoolSetting.Builder()
            .name("从上次开始")
            .description("是否使用进度")
            .defaultValue(false)
            .build());

    // 上次的圈数
    private final Setting<Integer> lastCircle = sgRestart.add(new IntSetting.Builder()
            .name("上次圈数")
            .description("上次圈数")
            .visible(lastBegin::get)
            .defaultValue(0)
            .min(0)
            .max(1000)
            .sliderMin(0)
            .sliderMax(100)
            .build());

    // 上次暂停的区块X
    private final Setting<Integer> lastChunkX = sgRestart.add(new IntSetting.Builder()
            .name("上次暂停的区块X")
            .description("上次暂停的区块X")
            .visible(lastBegin::get)
            .defaultValue(0)
            .min(Integer.MIN_VALUE)
            .max(Integer.MAX_VALUE)
            .sliderMin(Integer.MIN_VALUE)
            .sliderMax(Integer.MAX_VALUE)
            .build());

    // 上次暂停的区块Z
    private final Setting<Integer> lastChunkZ = sgRestart.add(new IntSetting.Builder()
            .name("上次暂停的区块Z")
            .description("上次暂停的区块Z")
            .visible(lastBegin::get)
            .defaultValue(0)
            .min(Integer.MIN_VALUE)
            .max(Integer.MAX_VALUE)
            .sliderMin(Integer.MIN_VALUE)
            .sliderMax(Integer.MAX_VALUE)
            .build());

    // 上次到哪个方向了
    private final Setting<PathEnum> lastPath = sgRestart.add(new EnumSetting.Builder<PathEnum>()
            .name("上次的路径点")
            .description("上次的路径点")
            .visible(lastBegin::get)
            .defaultValue(PathEnum.NEXT_CIRCLE)
            .build());

    // 上次开始的原点区块X
    private final Setting<Integer> lastOriginX = sgRestart.add(new IntSetting.Builder()
            .name("上次开始的原点区块X")
            .description("上次开始的原点区块X")
            .visible(lastBegin::get)
            .defaultValue(0)
            .min(Integer.MIN_VALUE)
            .max(Integer.MAX_VALUE)
            .sliderMin(Integer.MIN_VALUE)
            .sliderMax(Integer.MAX_VALUE)
            .build());

    // 上次开始的原点区块Z
    private final Setting<Integer> lastOriginZ = sgRestart.add(new IntSetting.Builder()
            .name("上次开始的原点区块Z")
            .description("上次开始的原点区块Z")
            .visible(lastBegin::get)
            .defaultValue(0)
            .min(Integer.MIN_VALUE)
            .max(Integer.MAX_VALUE)
            .sliderMin(Integer.MIN_VALUE)
            .sliderMax(Integer.MAX_VALUE)
            .build());

    // 渲染距离设置
    public final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("渲染距离")
            .description("渲染距离（区块）")
            .defaultValue(32)
            .min(6)
            .max(128)
            .sliderMin(6)
            .sliderMax(128)
            .build());

    // 渲染高度设置
    public final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
            .name("渲染高度")
            .description("渲染高度")
            .defaultValue(0)
            .min(-64)
            .max(320)
            .sliderMin(-64)
            .sliderMax(320)
            .build());

    // 形状模式设置
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("渲染模式")
            .description("渲染形状模式")
            .defaultValue(ShapeMode.Both)
            .build());

    // 预加载圈数设置
    private final Setting<Integer> preloadCircles = sgRender.add(new IntSetting.Builder()
            .name("预加载圈数")
            .description("预加载的圈数")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMin(1)
            .sliderMax(10)
            .build());

    // 目标区块颜色设置
    private final Setting<SettingColor> targetChunksSideColor = sgRender.add(new ColorSetting.Builder()
            .name("目标区块面颜色")
            .description("目标区块面颜色")
            .defaultValue(new SettingColor(255, 0, 0, 95))
            .visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
            .build());

    private final Setting<SettingColor> targetChunksLineColor = sgRender.add(new ColorSetting.Builder()
            .name("目标区块线颜色")
            .description("目标区块线条颜色")
            .defaultValue(new SettingColor(255, 0, 0, 205))
            .visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
            .build());

    // 已访问区块颜色设置
    private final Setting<SettingColor> visitedChunksSideColor = sgRender.add(new ColorSetting.Builder()
            .name("已访问区块面颜色")
            .description("已访问区块面颜色")
            .defaultValue(new SettingColor(0, 255, 0, 40))
            .visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
            .build());

    private final Setting<SettingColor> visitedChunksLineColor = sgRender.add(new ColorSetting.Builder()
            .name("已访问区块线颜色")
            .description("已访问区块线颜色")
            .defaultValue(new SettingColor(0, 255, 0, 80))
            .visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
            .build());

    // 当前路径区块颜色设置
    private final Setting<SettingColor> currentPathSideColor = sgRender.add(new ColorSetting.Builder()
            .name("当前路径区块面颜色")
            .description("当前路径区块面颜色")
            .defaultValue(new SettingColor(255, 255, 0, 60))
            .visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
            .build());

    private final Setting<SettingColor> currentPathLineColor = sgRender.add(new ColorSetting.Builder()
            .name("当前路径区块线颜色")
            .description("当前路径区块线颜色")
            .defaultValue(new SettingColor(255, 255, 0, 100))
            .visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
            .build());

    public BaseFinderXin() {
        super(EasyAddon.CATEGORY, "base-finder-xin", "base-finder-xin");
    }

    @Override
    public void onActivate() {
        if (mc.player == null)
            return;

        // 检查是否从上次开始,加载参数
        if (lastBegin.get()) {
            // 从上次开始,加载参数
            currentCircle = lastCircle.get();
            currentPath = lastPath.get();
            originChunk = new ChunkPos(lastOriginX.get(), lastOriginZ.get());
        } else {
            // 从当前位置开始
            currentCircle = 0;
            currentPath = PathEnum.NEXT_CIRCLE;
            originChunk = mc.player.getChunkPos();
        }

        // 清空之前的区块数据
        targetChunks.clear();
        visitedChunks.clear();
        currentPathChunks.clear();

        // 预先计算目标区块
        preloadTargetChunks();

        // 如果设置了从上次开始，设置目标区块 targetChunk
        isBack = !lastBegin.get();
        if (!isBack) {
            targetChunk = new ChunkPos(lastChunkX.get(), lastChunkZ.get());
        }
    }

    // 预先计算目标区块的方法
    private void preloadTargetChunks() {
        targetChunks.clear();

        // 预加载当前圈数+预加载圈数的区块
        int maxPreloadCircle = Math.min(currentCircle + preloadCircles.get(), circleLimit.get());
        for (int circle = currentCircle; circle <= maxPreloadCircle; circle++) {
            addCircleChunks(circle);
        }
    }

    // 添加指定圈数的所有区块
    private void addCircleChunks(int circle) {
        int radius = chunkLoadRadius.get() * circle * 2;

        // 从中心到正左
        ChunkPos centerLeft = new ChunkPos(originChunk.x - radius, originChunk.z);
        targetChunks.add(centerLeft);

        // 正左到左上
        ChunkPos upLeft = new ChunkPos(originChunk.x - radius, originChunk.z - radius);
        addPathChunks(centerLeft, upLeft);

        // 左上到右上
        ChunkPos upRight = new ChunkPos(originChunk.x + radius, originChunk.z - radius);
        addPathChunks(upLeft, upRight);

        // 右上到右下
        ChunkPos downRight = new ChunkPos(originChunk.x + radius, originChunk.z + radius);
        addPathChunks(upRight, downRight);

        // 右下到左下
        ChunkPos downLeft = new ChunkPos(originChunk.x - radius, originChunk.z + radius);
        addPathChunks(downRight, downLeft);

        // 左下到正左（完成一圈）
        addPathChunks(downLeft, centerLeft);
    }

    // 添加两点之间路径上的所有区块
    private void addPathChunks(ChunkPos from, ChunkPos to) {
        int deltaX = to.x - from.x;
        int deltaZ = to.z - from.z;

        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        // 如果起点和终点相同，直接添加该区块
        if (steps == 0) {
            targetChunks.add(from);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            int x = from.x + (deltaX * i) / steps;
            int z = from.z + (deltaZ * i) / steps;
            targetChunks.add(new ChunkPos(x, z));
        }
    }

    // 更新当前路径区块
    private void updateCurrentPathChunks() {
        currentPathChunks.clear();

        if (originChunk == null || currentPath == null)
            return;

        int radius = chunkLoadRadius.get() * currentCircle * 2;

        switch (currentPath) {
            case CENTER_TO_LEFT -> {
                ChunkPos from = originChunk;
                ChunkPos to = new ChunkPos(originChunk.x - radius, originChunk.z);
                addCurrentPathChunks(from, to);
            }
            case CENTER_LEFT_TO_UP_LEFT -> {
                ChunkPos from = new ChunkPos(originChunk.x - radius, originChunk.z);
                ChunkPos to = new ChunkPos(originChunk.x - radius, originChunk.z - radius);
                addCurrentPathChunks(from, to);
            }
            case UP_LEFT_TO_UP_RIGHT -> {
                ChunkPos from = new ChunkPos(originChunk.x - radius, originChunk.z - radius);
                ChunkPos to = new ChunkPos(originChunk.x + radius, originChunk.z - radius);
                addCurrentPathChunks(from, to);
            }
            case UP_RIGHT_TO_DOWN_RIGHT -> {
                ChunkPos from = new ChunkPos(originChunk.x + radius, originChunk.z - radius);
                ChunkPos to = new ChunkPos(originChunk.x + radius, originChunk.z + radius);
                addCurrentPathChunks(from, to);
            }
            case DOWN_RIGHT_TO_DOWN_LEFT -> {
                ChunkPos from = new ChunkPos(originChunk.x + radius, originChunk.z + radius);
                ChunkPos to = new ChunkPos(originChunk.x - radius, originChunk.z + radius);
                addCurrentPathChunks(from, to);
            }
            case DOWN_LEFT_TO_LEFT -> {
                ChunkPos from = new ChunkPos(originChunk.x - radius, originChunk.z + radius);
                ChunkPos to = new ChunkPos(originChunk.x - radius, originChunk.z);
                addCurrentPathChunks(from, to);
            }
        }
    }

    // 添加当前路径区块
    private void addCurrentPathChunks(ChunkPos from, ChunkPos to) {
        int deltaX = to.x - from.x;
        int deltaZ = to.z - from.z;

        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        for (int i = 0; i <= steps; i++) {
            int x = from.x + (deltaX * i) / steps;
            int z = from.z + (deltaZ * i) / steps;
            currentPathChunks.add(new ChunkPos(x, z));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            setForwardForced(false);
            return;
        }

        updateCurrentPathChunks();

        ChunkPos playerChunk = mc.player.getChunkPos();
        visitedChunks.add(playerChunk);

        if (!isBack) {
            if (mc.player.getChunkPos().equals(targetChunk)) {
                isBack = true;
            }
            setForwardForced(false);
            return;
        }

        if (currentCircle > circleLimit.get()) {
            setForwardForced(false);
            info("搜索完成");
            toggle();
            return;
        }

        if (currentPath == PathEnum.NEXT_CIRCLE) {
            updateNextPath();
            currentCircle++;
            info("前往第" + currentCircle + "圈...");

            int maxPreloadCircle = currentCircle + preloadCircles.get();
            if (maxPreloadCircle <= circleLimit.get()) {
                for (int circle = currentCircle; circle <= maxPreloadCircle; circle++) {
                    addCircleChunks(circle);
                }
            }
        }

        switch (currentPath) {
            case CENTER_TO_LEFT, DOWN_LEFT_TO_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2, originChunk.z);
            case CENTER_LEFT_TO_UP_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2,
                    originChunk.z - chunkLoadRadius.get() * currentCircle * 2);
            case UP_LEFT_TO_UP_RIGHT ->
                targetChunk = new ChunkPos(originChunk.x + chunkLoadRadius.get() * currentCircle * 2,
                    originChunk.z - chunkLoadRadius.get() * currentCircle * 2);
            case UP_RIGHT_TO_DOWN_RIGHT ->
                targetChunk = new ChunkPos(originChunk.x + chunkLoadRadius.get() * currentCircle * 2,
                    originChunk.z + chunkLoadRadius.get() * currentCircle * 2);
            case DOWN_RIGHT_TO_DOWN_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2,
                    originChunk.z + chunkLoadRadius.get() * currentCircle * 2);
        }

        if (mc.player.getChunkPos().equals(targetChunk)) {
            setForwardForced(false);
            if (turnDelayTimer == 0) {
                turnDelayTimer = turnDelay.get();
                return;
            }

            if (turnDelayTimer == 1) {
                updateNextPath();
                info("已完成第" + currentCircle + "圈的 " + currentPath);
            }

            turnDelayTimer--;
            return;
        }

        if (targetChunk == null || turnDelayTimer > 0) {
            setForwardForced(false);
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = new Vec3d(targetChunk.getStartX() + 8, mc.player.getY(), targetChunk.getStartZ() + 8);

        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;

        double distance2D = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (distance2D < 1.0) {
            setForwardForced(false);
            return;
        }

        targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        mc.player.setYaw(targetYaw);

        if (waitChunkLoad.get()) {
            Direction playerDirection = Direction.fromRotation(targetYaw);
            if (!AdjacentChunksLoaded(playerDirection)) {
                setForwardForced(false);
                return;
            }

            int chunkX = (int) (mc.player.getX() / 16);
            int chunkZ = (int) (mc.player.getZ() / 16);
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                setForwardForced(false);
                return;
            }
        }

        if (moveSpeed.get() > 1.0) {
            mc.player.setSprinting(true);
        }

        setForwardForced(true);
    }

    @Override
    public void onDeactivate() {
        setForwardForced(false);
        info("你可以保存此信息以便于下次重新开始: ");
        if (originChunk != null) {
            info("originX（起始区块X）: " + originChunk.x);
            info("originZ（起始区块Z）: " + originChunk.z);
        }
        info("circle（圈数）: " + currentCircle);
        info("currentPath（圈进度）: " + currentPath);

        // 保存进度信息
        if (originChunk != null && mc.player != null) {
            lastOriginX.set(originChunk.x);
            lastOriginZ.set(originChunk.z);
            lastChunkX.set(mc.player.getChunkPos().x);
            lastChunkZ.set(mc.player.getChunkPos().z);
            lastCircle.set(currentCircle);
            lastPath.set(currentPath);
        }

        // 清空区块数据
        targetChunks.clear();
        visitedChunks.clear();
        currentPathChunks.clear();

        // 还原变量
        originChunk = null;
        targetChunk = null;
        currentPath = null;
    }

    private void setForwardForced(boolean pressed) {
        if (mc.options == null) return;

        if (pressed) {
            mc.options.forwardKey.setPressed(true);
            forcingForward = true;
        } else if (forcingForward) {
            mc.options.forwardKey.setPressed(false);
            forcingForward = false;
        }
    }

    // 检查当前区块左右两边区块是否已完全加载
    private boolean AdjacentChunksLoaded(Direction direction) {
        ChunkPos currentChunk = mc.player.getChunkPos();

        for (int i = -chunkLoadRadius.get(); i <= chunkLoadRadius.get(); i++) {
            ChunkPos adjacentChunk = null;
            switch (direction) {
                case NORTH, SOUTH ->
                    adjacentChunk = new ChunkPos(currentChunk.x + i, currentChunk.z);
                case EAST, WEST -> adjacentChunk = new ChunkPos(currentChunk.x, currentChunk.z + i);
            }
            if (!mc.world.getChunkManager().isChunkLoaded(adjacentChunk.x, adjacentChunk.z)) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null)
            return;

        BlockPos playerPos = new BlockPos(mc.player.getBlockX(), renderHeight.get(), mc.player.getBlockZ());
        double renderDistanceBlocks = renderDistance.get() * 16.0;

        // 只渲染目标区块，根据状态使用不同颜色
        synchronized (targetChunks) {
            for (ChunkPos chunk : targetChunks) {
                if (chunk != null && playerPos.isWithinDistance(
                        new BlockPos(chunk.getCenterX(), renderHeight.get(), chunk.getCenterZ()),
                        renderDistanceBlocks)) {

                    // 根据区块状态选择颜色
                    SettingColor sideColor, lineColor;

                    if (currentPathChunks.contains(chunk)) {
                        // 当前路径区块
                        sideColor = currentPathSideColor.get();
                        lineColor = currentPathLineColor.get();
                    } else if (visitedChunks.contains(chunk)) {
                        // 已访问区块
                        sideColor = visitedChunksSideColor.get();
                        lineColor = visitedChunksLineColor.get();
                    } else {
                        // 未访问的目标区块
                        sideColor = targetChunksSideColor.get();
                        lineColor = targetChunksLineColor.get();
                    }

                    if (sideColor.a > 5 || lineColor.a > 5) {
                        renderChunk(chunk, sideColor, lineColor, event);
                    }
                }
            }
        }
    }

    // 渲染单个区块的方法
    private void renderChunk(ChunkPos chunk, SettingColor sideColor, SettingColor lineColor, Render3DEvent event) {
        Box box = new Box(
                new Vec3d(chunk.getStartX(), renderHeight.get(), chunk.getStartZ()),
                new Vec3d(chunk.getEndX() + 1, renderHeight.get() + 1, chunk.getEndZ() + 1));

        event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                sideColor, lineColor, shapeMode.get(), 0);
    }

    // 更新下一个路径
    private void updateNextPath() {
        switch (currentPath) {
            case NEXT_CIRCLE -> currentPath = PathEnum.CENTER_TO_LEFT;
            case CENTER_TO_LEFT -> currentPath = PathEnum.CENTER_LEFT_TO_UP_LEFT;
            case CENTER_LEFT_TO_UP_LEFT -> currentPath = PathEnum.UP_LEFT_TO_UP_RIGHT;
            case UP_LEFT_TO_UP_RIGHT -> currentPath = PathEnum.UP_RIGHT_TO_DOWN_RIGHT;
            case UP_RIGHT_TO_DOWN_RIGHT -> currentPath = PathEnum.DOWN_RIGHT_TO_DOWN_LEFT;
            case DOWN_RIGHT_TO_DOWN_LEFT -> currentPath = PathEnum.DOWN_LEFT_TO_LEFT;
            case DOWN_LEFT_TO_LEFT -> currentPath = PathEnum.NEXT_CIRCLE;
        }
    }

    // ==================== 速度获取和设置方法 ====================

    /**
     * 获取玩家当前的X轴速度
     */
    private double getX() {
        return mc.player.getVelocity().x;
    }

    /**
     * 获取玩家当前的Y轴速度
     */
    private double getY() {
        return mc.player.getVelocity().y;
    }

    /**
     * 获取玩家当前的Z轴速度
     */
    private double getZ() {
        return mc.player.getVelocity().z;
    }

    /**
     * 设置玩家的X轴速度
     * 保持Y和Z轴速度不变
     */
    private void setX(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(f, currentVel.y, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置玩家的Y轴速度
     * 保持X和Z轴速度不变
     */
    private void setY(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(currentVel.x, f, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置玩家的Z轴速度
     * 保持X和Y轴速度不变
     */
    private void setZ(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(currentVel.x, currentVel.y, f);
        mc.player.setVelocity(newVel);
    }

    public enum PathEnum {
        NEXT_CIRCLE,
        CENTER_TO_LEFT,
        CENTER_LEFT_TO_UP_LEFT,
        UP_LEFT_TO_UP_RIGHT,
        UP_RIGHT_TO_DOWN_RIGHT,
        DOWN_RIGHT_TO_DOWN_LEFT,
        DOWN_LEFT_TO_LEFT
    }
}
