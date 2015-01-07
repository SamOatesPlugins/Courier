package se.troed.plugin.Courier;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

class CourierCommands /*extends ServerListener*/ implements CommandExecutor {
    private final Courier plugin;
    private final Tracker tracker;

    public CourierCommands(Courier instance) {
        plugin = instance;
        tracker = plugin.getTracker();
    }
    
    // Player is null for console
    // This method didn't turn out that well. Should send a message to sender, when console,
    // about why the command fails when we need a player object
    private boolean allowed(Player p, String c) {
        boolean a = false;
        if (p != null) {
            if(c.equals(Courier.CMD_POSTMAN) && p.hasPermission(Courier.PM_POSTMAN)) {
                a = true;
            } else if(c.equals(Courier.CMD_COURIER) && p.hasPermission(Courier.PM_INFO)) {
                a = true;
            } else if(c.equals(Courier.CMD_POST) && p.hasPermission(Courier.PM_SEND)) {
                a = true;
            } else if(c.equals(Courier.CMD_LETTER) && p.hasPermission(Courier.PM_WRITE)) {
                a = true;
            } if(c.equals(Courier.CMD_COURIER)) {
                a = true;
            }
            plugin.getCConfig().clog(Level.FINE, "Player command event");
        } else {
            // console operator is op, no player and no location
            plugin.getCConfig().clog(Level.FINE, "Server command event");
        }
        plugin.getCConfig().clog(Level.FINE, "Permission: " + a);
        return a;
    }

    /*
    postman:
        description: Spawns a postman
        aliases: [mailman, postie]
        permission: courier.postman
        usage: /postman
    */
    boolean commandInformation(Player player, String[] args) {
        boolean retVal = false;
        if (args != null && args.length > 0) {
            if (args[0].equalsIgnoreCase("fees")) {
                if(plugin.getEconomy() != null) {
                    double fee = plugin.getCConfig().getFeeSend();
                    Courier.display(player, plugin.getCConfig().getInfoFee(plugin.getEconomy().format(fee)));
                } else {
                    Courier.display(player, plugin.getCConfig().getInfoNoFee());
                }
                if(!plugin.getCConfig().getFreeLetter()) {
                    // letters aren't free on this server
                    List<ItemStack> resources = plugin.getCConfig().getLetterResources();
                    Courier.display(player, plugin.getCConfig().getLetterInfoCost(resources.toString().replaceAll("[\\[\\]\\{\\}]|ItemStack", "")));
                } else {
                    Courier.display(player, plugin.getCConfig().getLetterInfoFree());
                }
                retVal = true;
            } else if(args[0].equalsIgnoreCase("unread")) {
                if(plugin.getCourierdb().deliverUnreadMessages(player.getName())) {
                    Courier.display(player, plugin.getCConfig().getPostmanExtraDeliveries());
                } else {
                    Courier.display(player, plugin.getCConfig().getPostmanNoUnreadMail());
                }
                retVal = true;
            }
            // todo: implement /courier list
        } else {
            Courier.display(player, plugin.getCConfig().getInfoLine1());
            Courier.display(player, plugin.getCConfig().getInfoLine2());
            Courier.display(player, plugin.getCConfig().getInfoLine3());
            Courier.display(player, plugin.getCConfig().getInfoLine4());
            retVal = true;
        }
        return retVal;
    }

    /*
    postman:
        description: Spawns a postman
        aliases: [mailman, postie]
        permission: courier.postman
        usage: /postman
     */
    boolean commandPostman(Player player) {
        if(plugin.getCourierdb().undeliveredMail(player.getName())) {
            int undeliveredMessageId = plugin.getCourierdb().undeliveredMessageId(player.getName());
            if(undeliveredMessageId != -1) {
                // this is really a command meant for testing, no need for translation
                Courier.display(player, "You've got mail waiting for delivery!");

                plugin.getCConfig().clog(Level.FINE, "MessageId: " + undeliveredMessageId);
                String from = plugin.getCourierdb().getSender(player.getName(), undeliveredMessageId);
                String message = plugin.getCourierdb().getMessage(player.getName(), undeliveredMessageId);
                plugin.getCConfig().clog(Level.FINE, "Sender: " + from + " Message: " + message);
                if(from != null && message != null) {
                    Location spawnLoc = plugin.findSpawnLocation(player);
                    if(spawnLoc != null) {
//                        Postman postman = new CreaturePostman(plugin, player, undeliveredMessageId);
                        Postman postman = Postman.create(plugin, player, undeliveredMessageId);
                        tracker.addSpawner(spawnLoc, postman);
                        postman.spawn(spawnLoc);
                        tracker.addPostman(postman);
                    }

                } else {
                    plugin.getCConfig().clog(Level.SEVERE, "Gotmail but no sender or message found! mapId=" + undeliveredMessageId);
                }
            } else {
                plugin.getCConfig().clog(Level.WARNING, "Gotmail but no mailid!");
            }
        }
        return true;
    }

    /*
    post:
        description: Sends the letter currently held in hand to someone
        aliases: [mail, send]
        permission: courier.send
        usage: /post playername
    */
    boolean commandPost(Player player, String[] args) {
        boolean ret = false;
        ItemStack item = player.getItemInHand();
        Letter letter = null;
        if(item != null && item.getType() == Material.MAP) {
            letter = tracker.getLetter(item);
        }
        if(letter != null) {
            if(plugin.getEconomy() != null &&
                    plugin.getEconomy().getBalance(player.getName()) < plugin.getCConfig().getFeeSend() &&
                    !player.hasPermission(Courier.PM_THEONEPERCENT)) {
                Courier.display(player, plugin.getCConfig().getPostNoCredit(plugin.getEconomy().format(plugin.getCConfig().getFeeSend())));
                ret = true;
            } else if(args == null || args.length < 1) {
                Courier.display(player, plugin.getCConfig().getPostNoRecipient());
            // /post player1 player2 player3 etc in the future?
            } else {
                String receiver = args[0];
                if(!letter.isAllowedToSee(player)) {
                    // fishy, this player is not allowed to see this Letter
                    // only agree to sending it on to the intended receiver
                    // silent substitution to correct receiver - Postmen can read envelopes you know ;)
                    receiver = letter.getReceiver();
                    plugin.getCConfig().clog(Level.FINE, player.getName() + " tried to send a Letter meant for " + letter.getReceiver() + " to " + args[0]);
                }
                OfflinePlayer[] offPlayers = plugin.getServer().getOfflinePlayers();
                OfflinePlayer p = null;
                for(OfflinePlayer o : offPlayers) {
                    if(o.getName().equalsIgnoreCase(receiver)) {
                        p = o;
                        plugin.getCConfig().clog(Level.FINE, "Found " + p.getName() + " in OfflinePlayers");
                        break;
                    }
                }
                if(p == null) {
                    // still not found, try lazy matching and display suggestions
                    // (searches online players only)
                    List<Player> players = plugin.getServer().matchPlayer(receiver);
                    if(players != null && players.size() == 1) {
                        // we got one exact match
                        // p = players.get(0); // don't, could be embarrassing if wrong
                        Courier.display(player, plugin.getCConfig().getPostDidYouMean(receiver, players.get(0).getName()));
                    } else if (players != null && players.size() > 1 && player.hasPermission(Courier.PM_LIST)) {
                        // more than one possible match found
                        StringBuilder suggestList = new StringBuilder();
                        int width = 0;
                        for(Player pl : players) {
                            suggestList.append(pl.getName());
                            suggestList.append(" ");
                            width += pl.getName().length()+1;
                            if(width >= 60) { // console max seems to be 60 .. or 61
                                suggestList.append("\n");
                                width = 0;
                            }
                        }
                        // players listing who's online. If so, that could be a permission also valid for /courier list
                        Courier.display(player, plugin.getCConfig().getPostDidYouMeanList(receiver));
                        Courier.display(player, plugin.getCConfig().getPostDidYouMeanList2(suggestList.toString()));
                    } else {
                        // time to give up
                        Courier.display(player, plugin.getCConfig().getPostNoSuchPlayer(receiver));
                    }
                }
                if(p != null) {
                    boolean send = false;

                    if(plugin.getEconomy() != null && !player.hasPermission(Courier.PM_THEONEPERCENT)) {
                        // withdraw postage fee
                        double fee = plugin.getCConfig().getFeeSend();
                        EconomyResponse er = plugin.getEconomy().withdrawPlayer(player.getName(), fee);
                        if(er.transactionSuccess()) {
                            Courier.display(player, plugin.getCConfig().getPostLetterSentFee(p.getName(), plugin.getEconomy().format(fee)));
                            send = true;
                        } else {
                            Courier.display(player, plugin.getCConfig().getPostFundProblem());
                            plugin.getCConfig().clog(Level.WARNING, "Could not withdraw postage fee from " + p.getName());
                        }
                        // add postage fee to bank account if one has been configured
                        // allows both bank accounts and player accounts
                        String account = plugin.getCConfig().getBankAccount();
                        if(account != null && !account.isEmpty() && !account.equalsIgnoreCase("<none>")) {
                            if(plugin.getEconomy().getBanks().contains(account)) {
                                // named Bank Account exists
                                er = plugin.getEconomy().bankDeposit(account, fee);
                                plugin.getCConfig().clog(Level.FINE, "Depositing fee into bank account " + account);
                            } else if (plugin.getEconomy().hasAccount(account)) {
                                // it's a Player
                                er = plugin.getEconomy().depositPlayer(account, fee);
                                plugin.getCConfig().clog(Level.FINE, "Depositing fee into player account " + account);
                            } else {
                                // config is in error
                                plugin.getCConfig().clog(Level.WARNING, "Configured Post office account " + account + " does not exist.");
                            }
                            if(!er.transactionSuccess()) {
                                plugin.getCConfig().clog(Level.WARNING, "Could not add postage fee to configured account: " + account);
                            }
                        }
                    } else {
                        Courier.display(player, plugin.getCConfig().getPostLetterSent(p.getName()));
                        send = true;
                    }
                    if(send) {
                        // sign over this letter to recipient
                        if(plugin.getCourierdb().sendMessage(letter.getId(), p.getName(), player.getName())) {
                            // existing Letter now has outdated info, will automatically be recreated from db
                            tracker.removeLetter(letter.getId());
//                            plugin.getLetterRenderer().forceClear();

                            // remove item from hands, which kills the ItemStack association. It's now "gone"
                            // from the control of this player. (if I implement additional receivers you could of course
                            //  cc: yourself)
                            player.setItemInHand(null);
                        } else {
                            plugin.getCConfig().clog(Level.WARNING, "Could not send message with ID: " + letter.getId());
                        }
                    }
                }
                ret = true;
            }
        } else {
            Courier.display(player, plugin.getCConfig().getPostNoLetter());
        }
        return ret;
    }

    /*
    letter:
        description: Creates a letter
        aliases: [note, write]
        permission: courier.write
        usage: /letter message        # Can be repeated to add additional text to existing held letter
     */
    boolean commandLetter(Player player, String[] args) {
        // letter - no argument - chatmode?
        if(args == null || args.length < 1) {
            Courier.display(player, plugin.getCConfig().getLetterNoText());
            return false;
        }
        // letter message - builds upon message in hand
        boolean ret = false;
        try {
            ItemStack item = player.getItemInHand();
            Letter letter = null;
            boolean crafted = false;
            if(item != null) {
                if(plugin.courierMapType(item) == Courier.PARCHMENT) {
                    // crafted parchment can safely be replaced properly later
                    crafted = true;
                    plugin.getCConfig().clog(Level.FINE, "Found crafted letter");
                } else if(plugin.courierMapType(item) == Courier.LETTER) {
                    // this is a current Courier Letter
                    letter = tracker.getLetter(item);
                }
            }
            int id = -1;
            if(letter == null) {
                // player had no, or blank, Courier Letter in hand
                // see: http://dev.bukkit.org/server-mods/courier/tickets/16-postage-charges/
                id = createLetter(player, crafted);
            } else {
                id = letter.getId();
            }
            boolean securityBlocked = false;
            if(id != -1) {
                boolean useCached = true;
                StringBuilder message = new StringBuilder();
                if(letter != null && !letter.isAllowedToSee(player)) {
                    // oh my, we're not allowed to read this letter, just do nothing from here on
                    securityBlocked = true;
                } else if(letter != null) {
                    // new stuff appended to the old
                    // fetch from db, letter.getMessage contains newline formatted text
                    message.append(plugin.getCourierdb().getMessage(letter.getReceiver(), id));
                    if(!player.getName().equalsIgnoreCase(letter.getSender())) {
                        // we're adding to existing text from someone else, add newlines
                        // extra credits: detect if we were going to be on a new line anyway, then only append one
                        message.append("\\n \\n "); // replaced with actual newlines by Letter, later
                        useCached = false;
                    }
                }
                if(!securityBlocked) {
                    // hey I added &nl a newline -> &nl      -> &nl
                    // hey I added\n a newline   -> added\n  -> added \n
                    // hey I added \na newline   -> \na      -> \n a
                    // hey I added\na newline    -> added\na -> added \n a
                    Pattern newlines = Pattern.compile("(\\s*\\\\n\\s*|\\s*&nl\\s*)");
                    boolean invalid = false;
                    try {
                        for (String arg : args) {
                            // http://dev.bukkit.org/server-mods/courier/tickets/34-illegal-argument-exception-in-map-font/
                            if(!MinecraftFont.Font.isValid(arg)) {
                                invalid = true;
                                continue;
                            }
                            // %loc -> [X,Y,Z] and such
                            // if this grows, break it out and make it configurable
                            if (arg.equalsIgnoreCase("%loc") || arg.equalsIgnoreCase("%pos")) {
                                Location loc = player.getLocation();
                                message.append("[");
                                message.append(loc.getBlockX());
                                message.append(",");
                                message.append(loc.getBlockY());
                                message.append(",");
                                message.append(loc.getBlockZ());
                                message.append("]");
                            } else {
                                message.append(newlines.matcher(arg).replaceAll(" $1 ").trim()); // tokenize
                            }
                            message.append(" ");
                        }
                    } catch (Exception e) {
                        plugin.getCConfig().clog(Level.SEVERE, "Caught Exception in MinecraftFont.isValid()");
                        invalid = true;
                    }
    
                    if(invalid) {
                        Courier.display(player, plugin.getCConfig().getLetterSkippedText());
                    }
    
                    if (plugin.getCourierdb().storeMessage(id,
                            player.getName(),
                            message.toString(), // .trim() but then /letter on /letter needs added space anyway
                            (int)(System.currentTimeMillis() / 1000L))) { // oh noes unix y2k issues!!!11
        
                        // no letter == we create and put in hands, or in inventory, or drop to ground
                        if(letter == null) {
                            ItemStack letterItem = new ItemStack(Material.MAP, 1, plugin.getCourierdb().getCourierMapId());
                            letterItem.addUnsafeEnchantment(Enchantment.DURABILITY, id);
                            letter = tracker.getLetter(letterItem);
                            // also see similar Lore code in CourierEventListener
                            ItemMeta meta = letterItem.getItemMeta();
                            if(meta != null) {
                                meta.setDisplayName("Courier Letter");
                                List<String> strings = new ArrayList<String>();
                                strings.add(letter.getTopRow());
                                meta.setLore(strings);
                                letterItem.setItemMeta(meta);
                            } else {
                                // ???
                            }

                            // if empty hands || crafted && itemInHand == single Courier parchment
                            //   setItemInHand
                            // else
                            //   addToInventory
                            //
                            // This code is very similar to code in EventListener when right clicking Postmen
                            if((item == null || item.getAmount() == 0) ||
                                    (crafted && item.getAmount() == 1 && plugin.courierMapType(item) == Courier.PARCHMENT)) {
                                plugin.getCConfig().clog(Level.FINE, "Letter delivered into player's hands");
                                player.setItemInHand(letterItem); // REALLY replaces what's there

                                // quick render
                                letter.setDirty(true);
//                                player.sendMap(plugin.getServer().getMap(plugin.getCourierdb().getCourierMapId()));
                            } else {
                                if(crafted && plugin.courierMapType(item) == Courier.PARCHMENT) {
                                    // subtract one parchment
                                    item.setAmount(item.getAmount()-1);
                                    player.setItemInHand(item);
                                }
                                plugin.getCConfig().clog(Level.FINE, "Player hands not empty");
                                HashMap<Integer, ItemStack> items = player.getInventory().addItem(letterItem);
                                if(items.isEmpty()) {
                                    plugin.getCConfig().clog(Level.FINE, "Letter added to inventory");
                                    Courier.display(player, plugin.getCConfig().getLetterInventory());
                                } else {
                                    plugin.getCConfig().clog(Level.FINE, "Inventory full, letter dropped");
                                    Courier.display(player, plugin.getCConfig().getLetterDrop());
                                    player.getWorld().dropItemNaturally(player.getLocation(), letterItem);
                                }
                            }
                        } else {
                            if(useCached) {
                                // set Message directly, we know no other info changed and want to keep current page info
                                letter.setMessage(message.toString());
                            } else {
                                // existing Letter now has outdated info, will automatically be recreated from db
                                tracker.removeLetter(id);
//                                plugin.getLetterRenderer().forceClear();
                            } 
                        }
                    } else {
                        Courier.display(player, plugin.getCConfig().getLetterCreateFailed());
                        plugin.getCConfig().clog(Level.SEVERE, "Could not store letter in database!");
                    }
                    ret = true;
                } else {
                    // we were security blocked
                    ret = true;
                }
            } else {
                // letter was never created, feedback why has been sent to player
                ret = true;
            }
        } catch (InternalError e) {
            Courier.display(player, plugin.getCConfig().getLetterNoMoreUIDs());
            plugin.getCConfig().clog(Level.SEVERE, "Out of unique message IDs!");
            ret = true;
        }
        return ret;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean ret = false;

        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        // sender is always safe to sendMessage to - console as well as player
        // player can from now on be null!

        String cmd = command.getName().toLowerCase();
        if(cmd.equals(Courier.CMD_COURIER) && allowed(player, cmd)) {
            // not allowed to be run from the console, uses player
            ret = commandInformation(player, args);
        } else if((cmd.equals(Courier.CMD_LETTER)) && allowed(player, cmd)) {
            // not allowed to be run from the console, uses player
            ret = commandLetter(player, args);
        } else if((cmd.equals(Courier.CMD_POST)) && allowed(player, cmd)) {
            // not allowed to be run from the console, uses player
            ret = commandPost(player, args);
        } else if(cmd.equals(Courier.CMD_POSTMAN) && allowed(player, cmd)){
            // not allowed to be run from the console, uses player
            ret = commandPostman(player);
        }
        return ret;
    }

    // helper methods

    @SuppressWarnings("deprecation") // player.updateInventory()
    int createLetter(Player player, boolean crafted) {
        int id = -1;
        if(!plugin.getCConfig().getFreeLetter()) {
            // letters aren't free on this server
            if(plugin.getCConfig().getRequiresCrafting()) {
                if(crafted) {
                    id = plugin.getCourierdb().generateUID();
                } else {
                    // Well, if we end up here the player hadn't crafted a Letter and we're not making one for him/her
                    Courier.display(player, plugin.getCConfig().getLetterNoCraftedFound());
                }
            } else {
                List<ItemStack> resources = plugin.getCConfig().getLetterResources();
                // verify player has the goods
                Inventory inv = player.getInventory();
                boolean lacking = false;
                for(ItemStack resource : resources) {
                    plugin.getCConfig().clog(Level.FINE, "Requiring resource: " + resource.toString());
                    // (ItemStack, amount) doesn't match, (Material, amount) does
                    if(!inv.contains(resource.getType(), resource.getAmount())) {
                        plugin.getCConfig().clog(Level.FINE, "Requiring resource: " + resource.toString() + " failed");
                        lacking = true;
                    }
                }
                if(lacking) {
                    Courier.display(player, plugin.getCConfig().getLetterLackingResources());
                } else {
                    // subtract from inventory
                    for(ItemStack resource : resources) {
                        inv.removeItem(resource);
                    }
                    player.updateInventory(); // deprecated, but apparently the correct thing to do
                    id = plugin.getCourierdb().generateUID();
                }
            }
        } else {
            // letters are free
            id = plugin.getCourierdb().generateUID();
        }
        return id;
    }
}