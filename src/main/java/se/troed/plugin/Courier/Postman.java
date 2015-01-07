package se.troed.plugin.Courier;

import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

/**
 * A Postman is a friendly Creature, tirelessly carrying around our mail
 *
 * One will be spawned for each Player that will receive mail
 */
public abstract class Postman {

    protected Creature postman;
    protected EntityType type;
    protected final Courier plugin;
    protected final ItemStack letterItem;
    protected UUID uuid;
    protected boolean scheduledForQuickRemoval;
    protected int taskId;
    protected Runnable runnable;
    protected final Player player;

    protected Postman(Courier plug, Player p, int id, EntityType t) {
        plugin = plug;
        player = p;
        type = t;
        // Postmen, like players doing /letter, can create actual Items
        letterItem = new ItemStack(Material.MAP, 1, plug.getCourierdb().getCourierMapId());
        letterItem.addUnsafeEnchantment(Enchantment.DURABILITY, id);
    }
    
    static Postman create(Courier plug, Player p, int id) {
        if(plug.getCConfig().getType() == EntityType.ENDERMAN) {
            return new EnderPostman(plug, p, id, plug.getCConfig().getType());
        } else {
            return new CreaturePostman(plug, p, id, plug.getCConfig().getType());
        }
    }

    // must be implemented
    public abstract void spawn(Location l);

    public Player getPlayer() {
        return player;
    }
    
    public EntityType getType() {
        return type;
    }

    // yes I know this fails in many cases, we only "promise" Endermen and Villagers for now
    // would need to contain all Creatures for this to work realiably
    static int getHeight(Courier plug) {
        EntityType type = plug.getCConfig().getType();
        if(type == EntityType.ENDERMAN) {
            return 3;
        } else if(type == EntityType.VILLAGER ||
                  type == EntityType.BLAZE ||
                  type == EntityType.COW ||
                  type == EntityType.CREEPER ||
                  type == EntityType.MUSHROOM_COW ||
                  type == EntityType.PIG_ZOMBIE ||
                  type == EntityType.SHEEP ||
                  type == EntityType.SKELETON ||
                  type == EntityType.SNOWMAN ||
                  type == EntityType.SQUID ||
                  type == EntityType.ZOMBIE) {
            return 2;
        } else {
            return 1;
        }
    }

    public ItemStack getLetterItem() {
        return letterItem;
    }

    public void cannotDeliver() {
        Courier.display(player, plugin.getCConfig().getCannotDeliver());
    }

    public void announce(Location l) {
        // todo: if in config, play effect
        player.playEffect(l, Effect.BOW_FIRE, 100);
        Courier.display(player, plugin.getCConfig().getGreeting());
    }
    
    public void drop() {
        postman.getWorld().dropItemNaturally(postman.getLocation(), letterItem);
        Courier.display(player, plugin.getCConfig().getMailDrop());
    }

    public UUID getUUID() {
        return uuid;
    }

    public void remove() {
        postman.remove();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean scheduledForQuickRemoval() {
        return scheduledForQuickRemoval;
    }
    
    public void setTaskId(int t) {
        taskId = t;
    }
    
    public int getTaskId() {
        return taskId;
    }

    public void setRunnable(Runnable r) {
        runnable = r;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    // Called when either mail has been delivered or someone is attacking the postman
    public void quickDespawn() {
        plugin.getTracker().schedulePostmanDespawn(this.uuid, plugin.getCConfig().getQuickDespawnTime());
        scheduledForQuickRemoval = true;
    }
}