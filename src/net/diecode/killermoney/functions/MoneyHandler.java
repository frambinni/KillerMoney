package net.diecode.killermoney.functions;

import net.diecode.killermoney.BukkitMain;
import net.diecode.killermoney.Utils;
import net.diecode.killermoney.configs.DefaultConfig;
import net.diecode.killermoney.configs.LangConfig;
import net.diecode.killermoney.enums.DivisionMethod;
import net.diecode.killermoney.enums.KMPermission;
import net.diecode.killermoney.enums.LanguageString;
import net.diecode.killermoney.events.*;
import net.diecode.killermoney.managers.EconomyManager;
import net.diecode.killermoney.managers.EntityManager;
import net.diecode.killermoney.managers.LanguageManager;
import net.diecode.killermoney.objects.EntityDamage;
import net.diecode.killermoney.objects.EntityProperties;
import net.diecode.killermoney.objects.MoneyProperties;
import net.diecode.killermoney.objects.WorldProperties;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

public class MoneyHandler implements Listener {

    private static String moneyItemDropLore = ChatColor.GRAY + "KillerMoney reward: ";
    private static BigDecimal earnedMoney = BigDecimal.ZERO;
    private static BigDecimal lostMoney = BigDecimal.ZERO;

    @EventHandler
    public void onMoneyProcessorEvent(KMMoneyProcessorEvent e) {
        if (e.isCancelled()) {
            return;
        }

        // For loop with all entity damagers
        for (EntityDamage ed : e.getDamagers()) {
            Player p = Bukkit.getPlayer(ed.getPlayerUUID());

            if (p == null) {
                continue;
            }

            if (ed.getCalculatedMoney().doubleValue() > 0) {
                if (DefaultConfig.isMoneyItemDropEnabled()) {
                    Bukkit.getPluginManager().callEvent(new KMMoneyItemDropEvent(generateItemStack(ed.getCalculatedMoney(),
                            e.getVictim().getType()), e.getVictim().getLocation()));
                } else {
                    Bukkit.getPluginManager().callEvent(new KMEarnMoneyDepositEvent(e.getMoneyProperties(), p,
                            ed.getCalculatedMoney(), e.getVictim(), ed.getDamage()));
                }
            } else {
                BigDecimal money = ed.getCalculatedMoney().multiply(new BigDecimal(-1));

                money = money.setScale(2, BigDecimal.ROUND_HALF_UP);

                Bukkit.getPluginManager().callEvent(new KMLoseMoneyEvent(e.getMoneyProperties(), p,
                        money, e.getVictim(), ed.getDamage()));
            }
        }
    }

    @EventHandler
    public void onEarnMoneyPickedUp(KMEarnMoneyPickedUpEvent e) {
        if (e.isCancelled()) {
            return;
        }

        // Increase value of the limiter
        if (!hasMoneyLimitBypass(e.getPlayer())) {
            e.getMoneyProperties().increaseLimitCounter(e.getPlayer().getUniqueId(), e.getAmount());
        }

        // Deposit money
        EconomyManager.deposit(e.getPlayer(), e.getAmount().doubleValue());

        // Send money earning message to player
        String message = LanguageManager.cGet(LanguageString.GENERAL_YOU_PICKED_UP_MONEY, e.getAmount().doubleValue());

        MessageHandler.process(e.getPlayer(), message);

        earnedMoney = earnedMoney.add(e.getAmount());
    }

    @EventHandler
    public void onEarnMoneyInstantDeposit(KMEarnMoneyDepositEvent e) {
        if (e.isCancelled()) {
            return;
        }

        // Increase value of the limiter
        if (!hasMoneyLimitBypass(e.getPlayer())) {
            e.getMoneyProperties().increaseLimitCounter(e.getPlayer().getUniqueId(), e.getAmount());
        }

        // Deposit money
        EconomyManager.deposit(e.getPlayer(), e.getAmount().doubleValue());

        // Send money earning message to player
        if (e.getPlayer() != null && e.getPlayer().isOnline()) {
            String message = LanguageManager.cGet(LanguageString.GENERAL_YOU_KILLED_AN_ENTITY_AND_EARN_MONEY,
                    e.getVictim().getType(), LangConfig.getStrings().get(LanguageString.valueOf("ENTITIES_"
                            + e.getVictim().getType().name())),
                    e.getAmount().doubleValue(), e.getDamage().doubleValue());

            MessageHandler.process(e.getPlayer(), message);
        }

        earnedMoney = earnedMoney.add(e.getAmount());
    }

    @EventHandler
    public void onLoseMoney(KMLoseMoneyEvent e) {
        if (e.isCancelled()) {
            return;
        }

        // Withdraw money
        EconomyManager.withdraw(e.getPlayer(), e.getAmount());

        // Send money losing message to player
        if (e.getPlayer() != null && e.getPlayer().isOnline()) {
            String message = LanguageManager.cGet(LanguageString.GENERAL_YOU_KILLED_AN_ENTITY_AND_LOSE_MONEY,
                    e.getVictim().getType(), LangConfig.getStrings().get(LanguageString.valueOf("ENTITIES_"
                            + e.getVictim().getType().name())),
                    e.getAmount().doubleValue(), e.getDamage().doubleValue());

            MessageHandler.process(e.getPlayer(), message);
        }

        lostMoney = lostMoney.add(e.getAmount());
    }

    @EventHandler
    public void onMoneyDropItem(KMMoneyItemDropEvent e) {
        e.getLocation().getWorld().dropItemNaturally(e.getLocation(), e.getItemStack());
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Item item = e.getEntity();
        ItemStack is = item.getItemStack();

        if (isMoneyItem(is)) {
            item.setCustomName(is.getItemMeta().getDisplayName());
            item.setCustomNameVisible(true);
        }
    }

    @EventHandler
    public void onItemPickUp(PlayerPickupItemEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Item item = e.getItem();
        ItemStack is = item.getItemStack();

        if (isMoneyItem(is)) {
            e.setCancelled(true);

            item.remove();
            BigDecimal money = new BigDecimal(Double.valueOf(item.getCustomName()
                    .replaceAll("[^0-9.]", "")))
                    .setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

            EntityType entityType = EntityType.valueOf(is.getItemMeta().getLore().get(1));
            EntityProperties ep = EntityManager.getEntityProperties(entityType);

            if (ep == null) {
                return;
            }

            WorldProperties wp = EntityManager.getWorldProperties(ep, e.getItem().getLocation().getWorld().getName());

            if (wp != null) {
                Bukkit.getPluginManager().callEvent(new KMEarnMoneyPickedUpEvent(wp.getMoneyProperties(),
                        e.getPlayer(), money));
            }
        }
    }

    public static void process(MoneyProperties mp, ArrayList<EntityDamage> damagers, LivingEntity victim,
                               Player killer) {
        if (BukkitMain.getEconomy() == null) {
            return;
        }

        if (!mp.chanceGen()) {
            return;
        }

        BigDecimal money = new BigDecimal(Utils.randomNumber(mp.getMinMoney(),
                mp.getMaxMoney())).setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);
        BigDecimal dividedMoney;
        BigDecimal remainingLimit;
        BigDecimal playerLimit;
        ArrayList<EntityDamage> filteredDamagers = new ArrayList<>();

        // Money earning (deposit)
        if (money.doubleValue() > 0) {

            // Division method is SHARED
            if (mp.getDivisionMethod() == DivisionMethod.SHARED) {
                for (EntityDamage ed : damagers) {
                    Player p = Bukkit.getPlayer(ed.getPlayerUUID());

                    if (!hasMoneyLimitBypass(p)) {
                        if (mp.isReachedLimit(ed.getPlayerUUID())) {
                            if (DefaultConfig.isReachedLimitMessage()) {
                                Bukkit.getPluginManager().callEvent(new KMLimitReachedEvent(p, victim));
                            }

                            continue;
                        }
                    }

                    // Get player's limit
                    playerLimit = mp.getLimit(killer);

                    // Calculating the money
                    BigDecimal x = ed.getDamage().divide(new BigDecimal(victim.getMaxHealth()), BigDecimal.ROUND_HALF_UP);

                    // Calculating remaining limit
                    remainingLimit = playerLimit.subtract(mp.getCurrentLimitValue(ed.getPlayerUUID()))
                            .setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

                    // Divided money by victim damage
                    dividedMoney = new BigDecimal(money.multiply(x).doubleValue());

                    // Use multipliers ( Permission based and global )
                    dividedMoney = dividedMoney.multiply(new BigDecimal(getMoneyMultiplier(p)));
                    dividedMoney = dividedMoney.multiply(new BigDecimal(MultiplierHandler.getMultiplier()));

                    if (!hasMoneyLimitBypass(p)) {
                        if ((mp.getLimit() > 0) && (dividedMoney.compareTo(remainingLimit) == 1)) {
                            dividedMoney = remainingLimit;
                        }
                    }

                    // Set scale
                    dividedMoney = dividedMoney.setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

                    ed.setCalculatedMoney(dividedMoney);
                    filteredDamagers.add(ed);
                }
            } else {
                // Division method is LAST_HIT
                if (!hasMoneyLimitBypass(killer)) {
                    if (mp.isReachedLimit(killer.getUniqueId())) {
                        if (DefaultConfig.isReachedLimitMessage()) {
                            Bukkit.getPluginManager().callEvent(new KMLimitReachedEvent(killer, victim));
                        }

                        return;
                    }
                }

                // Get player's limit
                playerLimit = mp.getLimit(killer);

                // Calculating remaining limit
                remainingLimit = playerLimit.subtract(mp.getCurrentLimitValue(killer.getUniqueId()));

                dividedMoney = money;

                if (!hasMoneyLimitBypass(killer)) {
                    if ((mp.getLimit() > 0) && (money.compareTo(remainingLimit) == 1)) {
                        dividedMoney = remainingLimit;
                    }
                }

                dividedMoney = dividedMoney.multiply(new BigDecimal(getMoneyMultiplier(killer)));
                dividedMoney = dividedMoney.multiply(new BigDecimal(MultiplierHandler.getMultiplier()));

                // Set scale
                dividedMoney = dividedMoney.setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

                filteredDamagers.add(new EntityDamage(killer.getUniqueId(),
                        new BigDecimal(victim.getMaxHealth()), dividedMoney));
            }
        }

        // Money losing (withdraw)
        if (money.doubleValue() < 0) {

            // Division method is SHARED
            if (mp.getDivisionMethod() == DivisionMethod.SHARED) {
                for (EntityDamage ed : damagers) {

                    // Calculating the money
                    BigDecimal x = ed.getDamage().divide(new BigDecimal(victim.getMaxHealth()), BigDecimal.ROUND_HALF_UP);

                    // Divided money by victim damage
                    dividedMoney = new BigDecimal(money.multiply(x).doubleValue());

                    // Set scale
                    dividedMoney = dividedMoney.setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

                    ed.setCalculatedMoney(dividedMoney);
                    filteredDamagers.add(ed);
                }
            } else {
                // Division method is LAST_HIT
                dividedMoney = money;

                // Set scale
                dividedMoney = dividedMoney.setScale(DefaultConfig.getDecimalPlaces(), BigDecimal.ROUND_HALF_UP);

                filteredDamagers.add(new EntityDamage(killer.getUniqueId(), new BigDecimal(victim.getMaxHealth()), dividedMoney));
            }
        }

        if (!filteredDamagers.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new KMMoneyProcessorEvent(mp, filteredDamagers, victim));
        }
    }

    private static double getMoneyMultiplier(Player p) {
        if (p != null) {
            for (Map.Entry<String, Double> multiplier : DefaultConfig.getMoneyMultipliers().entrySet()) {
                if (p.hasPermission(KMPermission.MONEY_MULTIPLIER.get() + "." + multiplier.getKey())) {
                    return multiplier.getValue();
                }
            }
        }

        return 1;
    }

    public static double getLimitMultiplier(Player p) {
        if (p != null) {
            for (Map.Entry<String, Double> multiplier : DefaultConfig.getLimitMultipliers().entrySet()) {
                if (p.hasPermission(KMPermission.LIMIT_MONEY_MULTIPLIER.get() + "." + multiplier.getKey())) {
                    return multiplier.getValue();
                }
            }
        }

        return 1;
    }

    private static boolean hasMoneyLimitBypass(Player player) {
        return (player != null) && player.hasPermission(KMPermission.BYPASS_MONEY_LIMIT.get());
    }

    public static ItemStack generateItemStack(final BigDecimal reward, final EntityType entityType) {
        ItemStack moneyItem = new ItemStack(DefaultConfig.getMoneyItemMaterial());
        ItemMeta im = moneyItem.getItemMeta();

        im.setDisplayName(DefaultConfig.getMoneyItemName()
                .replace("{amount}", String.valueOf(reward.doubleValue()))
        );

        ArrayList<String> lore = new ArrayList<String>() {
            {
                add(moneyItemDropLore + reward.doubleValue());
                add(entityType.name());
            }
        };

        im.setLore(lore);

        moneyItem.setItemMeta(im);
        moneyItem.addUnsafeEnchantment(Enchantment.DURABILITY, 1);

        return moneyItem;
    }

    public static boolean isMoneyItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        if (itemStack.getType() != DefaultConfig.getMoneyItemMaterial()) {
            return false;
        }

        if (!itemStack.containsEnchantment(Enchantment.DURABILITY)) {
            return false;
        }

        if (!itemStack.hasItemMeta()) {
            return false;
        }

        ItemMeta im = itemStack.getItemMeta();

        if (!im.hasDisplayName()) {
            return false;
        }

        if (!im.hasLore()) {
            return false;
        }

        return im.getLore().get(0).contains(moneyItemDropLore);
    }

    public static void resetMoneyCounter() {
        earnedMoney = BigDecimal.ZERO;
        lostMoney = BigDecimal.ZERO;
    }

    public static BigDecimal getEarnedMoney() {
        return earnedMoney;
    }

    public static BigDecimal getLostMoney() {
        return lostMoney;
    }
}
