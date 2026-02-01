package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ElytraReplace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private static final int ELYTRA_MAX_DAMAGE = new ItemStack(Items.ELYTRA).getMaxDamage();

    // 鞘翅替换耐久度阈值设置
    private final Setting<Integer> replaceDurability = sgGeneral.add(new IntSetting.Builder()
        .name("耐久度阈值")
        .description("鞘翅替换的耐久度阈值")
        .defaultValue(2)
        .range(1, ELYTRA_MAX_DAMAGE - 1)
        .sliderRange(1, ELYTRA_MAX_DAMAGE - 1)
        .build()
    );

    // 聊天反馈设置
    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("消息提醒")
        .description("鞘翅替换时发送消息提醒")
        .defaultValue(true)
        .build()
    );

    // 仅在飞行时替换设置
    private final Setting<Boolean> onlyWhenFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("仅飞行时替换")
        .description("仅在鞘翅飞行时替换")
        .defaultValue(false)
        .build()
    );



    // 新增：冲突处理设置组
    private final Setting<Boolean> temporaryDisableInventoryTweaks = sgGeneral.add(new BoolSetting.Builder()
        .name("InventoryTweaks兼容模式")
        .description("鞘翅替换时临时关闭InventoryTweaks模块")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reEnableDelay = sgGeneral.add(new IntSetting.Builder()
        .name("兼容模式延迟")
        .description("鞘翅替换后重新启用InventoryTweaks的延迟（tick）")
        .defaultValue(10)
        .range(1, 60)
        .sliderMax(60)
        .visible(temporaryDisableInventoryTweaks::get)
        .build()
    );

    // 新增：状态跟踪变量
    private boolean inventoryTweaksWasActive = false;
    private int reEnableCountdown = 0;

    public ElytraReplace() {
        super(EasyAddon.CATEGORY, "elytra-replace", "自动替换损坏的鞘翅");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // 检查玩家和世界是否存在
        if (mc.player == null || mc.world == null) return;

        // 新增：处理重新启用InventoryTweaks的倒计时
        if (reEnableCountdown > 0) {
            reEnableCountdown--;
            if (reEnableCountdown == 0 && inventoryTweaksWasActive) {
                reEnableInventoryTweaks();
            }
        }

        // 获取当前胸甲装备（鞘翅槽位）
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);

        // 检查当前装备是否为鞘翅
        if (chestStack.getItem() == Items.ELYTRA) {
            // 计算剩余耐久度
            int remainingDurability = chestStack.getMaxDamage() - chestStack.getDamage();

            // 检查是否需要替换鞘翅
            checkAndReplaceElytra(chestStack, remainingDurability);
        }
    }

    /**
     * 检查并替换鞘翅
     * @param chestStack 当前胸甲装备
     * @param remainingDurability 剩余耐久度
     */
    private void checkAndReplaceElytra(ItemStack chestStack, int remainingDurability) {
        // 检查是否只在飞行时替换
        if (onlyWhenFlying.get() && !mc.player.isFallFlying()) return;

        // 检查鞘翅是否需要替换
        if (remainingDurability > replaceDurability.get()) return;

        // 在背包中寻找替换用的鞘翅
        FindItemResult elytra = InvUtils.find(stack -> {
            if (stack.getItem() != Items.ELYTRA) return false;
            int stackDurability = stack.getMaxDamage() - stack.getDamage();
            return stackDurability > replaceDurability.get();
        });

        // 如果没有找到合适的鞘翅，发送警告
        if (!elytra.found()) {
            if (chatFeedback.get()) {
                warning("未找到耐久度 > %d 的替换鞘翅", replaceDurability.get());
            }
            return;
        }

        // 新增：临时关闭InventoryTweaks
        if (temporaryDisableInventoryTweaks.get()) {
            temporaryDisableInventoryTweaks();
        }

        // 执行鞘翅替换
        InvUtils.move().from(elytra.slot()).toArmor(2);

        // 发送替换成功的反馈信息
        if (chatFeedback.get()) {
            info("已替换鞘翅 (耐久度: %d -> %d)",
                remainingDurability,
                mc.player.getEquippedStack(EquipmentSlot.CHEST).getMaxDamage() -
                    mc.player.getEquippedStack(EquipmentSlot.CHEST).getDamage());
        }

        // 新增：设置重新启用倒计时
        if (temporaryDisableInventoryTweaks.get() && inventoryTweaksWasActive) {
            reEnableCountdown = reEnableDelay.get();
        }
    }

    /**
     * 新增：临时关闭InventoryTweaks模块
     */
    private void temporaryDisableInventoryTweaks() {
        InventoryTweaks inventoryTweaks = Modules.get().get(InventoryTweaks.class);
        if (inventoryTweaks != null && inventoryTweaks.isActive()) {
            inventoryTweaksWasActive = true;
            inventoryTweaks.toggle();
            if (chatFeedback.get()) {
                info("临时关闭InventoryTweaks模块");
            }
        } else {
            inventoryTweaksWasActive = false;
        }
    }

    /**
     * 新增：重新启用InventoryTweaks模块
     */
    private void reEnableInventoryTweaks() {
        InventoryTweaks inventoryTweaks = Modules.get().get(InventoryTweaks.class);
        if (inventoryTweaks != null && !inventoryTweaks.isActive()) {
            inventoryTweaks.toggle();
            if (chatFeedback.get()) {
                info("重新启用InventoryTweaks模块");
            }
        }
        inventoryTweaksWasActive = false;
    }



    @Override
    public void onDeactivate() {
        // 新增：模块关闭时，如果InventoryTweaks被临时关闭，则重新启用
        if (inventoryTweaksWasActive) {
            reEnableInventoryTweaks();
        }
        reEnableCountdown = 0;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestStack.getItem() != Items.ELYTRA) return "无鞘翅";

        int remainingDurability = chestStack.getMaxDamage() - chestStack.getDamage();
        return String.valueOf(remainingDurability);
    }


}
