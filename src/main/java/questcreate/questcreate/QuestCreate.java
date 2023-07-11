package questcreate.questcreate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class QuestCreate extends JavaPlugin implements Listener {
    private FileConfiguration questConfig;
    private FileConfiguration playerQuestConfig;
    private File questFile;
    private File playerQuestFile;
    private List<Quest> questList;
    private static QuestCreate instance;

    public static QuestCreate getInstance() {
        return instance;
    }
    @Override
    public void onEnable() {
        getLogger().info("퀘스트 플러그인 작동");
        resetPlayerQuestsAtSpecificTime();
        // 플러그인 활성화 시에 실행될 로직
        getServer().getPluginManager().registerEvents(this, this);
        questList = new ArrayList<>();
        questFile = new File(getDataFolder(), "quests.yml");
        playerQuestFile = new File(getDataFolder(), "playerQuest.yml");

        // 퀘스트 YAML 파일이 없으면 생성
        if (!questFile.exists()) {
            questFile.getParentFile().mkdirs();
            saveResource("quests.yml", false);
        }

        // 플레이어 퀘스트 YAML 파일이 없으면 생성
        if (!playerQuestFile.exists()) {
            playerQuestFile.getParentFile().mkdirs();
            saveResource("playerQuest.yml", false);
        }

        // YAML 파일을 메모리로 로드
        questConfig = YamlConfiguration.loadConfiguration(questFile);
        playerQuestConfig = YamlConfiguration.loadConfiguration(playerQuestFile);

        // 퀘스트 목록 초기화
        loadQuestList();
        instance = this;
    }

    @Override
    public void onDisable() {
        // 플러그인 비활성화 시에 실행될 로직
        getLogger().info("퀘스트 플러그인 종료");
//        saveQuestList();
        savePlayerQuests();
    }
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            String villagerName = villager.getCustomName();

            if (villagerName != null && villagerName.equals("일일퀘스트")) {
                boolean check = true;
                // 퀘스트 완료 보상
                ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(player.getUniqueId().toString());
                if (playerQuestSection != null) {
                    for (String questId : playerQuestSection.getKeys(false)) {
                        ConfigurationSection questSection = playerQuestSection.getConfigurationSection(questId);
                        if (questSection != null) {
                            String questName = questSection.getString("quest");
                            String itemString = questSection.getString("item");
                            Material layeritemcount = Material.getMaterial(itemString);
                            int itemCount = countItems(player.getInventory(), layeritemcount);
                            int amount = questSection.getInt("amount");
                            boolean progress = questSection.getBoolean("progress");
                            if (itemCount >= amount) {
                                if (!progress) {
                                    // 일정 개수만큼 아이템 제거
                                    player.getInventory().removeItem(new ItemStack(layeritemcount, amount));
                                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "iagive " + player.getName() + " protectblock:rkfcl_coin " + 1);
                                    questSection.set("progress", true);
                                    savePlayerQuests();
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                    check =false;
                                    sendTitle(player, "퀘스트 완료", "§a[ 일일 퀘스트 ] §f" + questName + " §a완료!", 10, 70, 10);
                                    break;
                                }
                            }
                        }
                    }
                }

                // 여기에 플레이어에게 퀘스트를 부여하는 로직을 구현합니다
                if (check) {
                    giveRandomQuest(player);
                }
            }
        }
    }

    private void sendTitle(Player player, String title, String subTitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
                ChatColor.BOLD + title,     // 타이틀 텍스트
                ChatColor.BOLD + subTitle,  // 타이틀 서브텍스트 (크고 굵은 텍스트로 표시됨)
                fadeIn,                    // 페이드인 시간 (틱 단위, 20틱 = 1초)
                stay,                      // 표시 시간 (틱 단위, 20틱 = 1초)
                fadeOut                    // 페이드아웃 시간 (틱 단위, 20틱 = 1초)
        );
    }
    private void giveRandomQuest(Player player) {
        UUID playerId = player.getUniqueId();
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        if (playerQuestSection != null) {
            int currentQuestCount = playerQuestSection.getKeys(false).size();
            if (currentQuestCount >= 3) {
                player.sendMessage("§c일일퀘스트는 하루에 3개까지 받을 수 있습니다.");
                return;
            }
        }
        // 설정된 퀘스트 목록에서 랜덤하게 퀘스트를 선택합니다
        if (!questList.isEmpty()) {
            Random random = new Random();
            int randomIndex = random.nextInt(questList.size());
            Quest quest = questList.get(randomIndex);
            if (quest != null) {
                // 이미 플레이어가 해당 퀘스트를 가지고 있는지 확인합니다
                boolean hasQuest = false;
                if (playerQuestSection != null) {
                    for (String questId : playerQuestSection.getKeys(false)) {
                        String questName = playerQuestSection.getString(questId + ".quest");
                        if (questName != null && questName.equals(quest.getName())) {
                            hasQuest = true;
                            break;
                        }
                    }
                }
                if (hasQuest) {
                    return;
                }
                // 퀘스트 받는 소리
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                int amount = quest.getAmount();
                player.sendMessage("§a퀘스트를 받았습니다!");
                player.sendMessage("§a[ 일일 퀘스트 ] §f" + quest.getName() + " " + amount + "개를 구해와");
                player.sendMessage("§7일일퀘스트는 하루에 3개까지 받을 수 있습니다.");

                // 플레이어 퀘스트 진행 상황을 저장합니다
                playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
                if (playerQuestSection == null) {
                    playerQuestSection = playerQuestConfig.createSection(playerId.toString());
                }
                String questId = "quest" + (playerQuestSection.getKeys(false).size() + 1);
                playerQuestSection.set(questId + ".quest", quest.getName());
                playerQuestSection.set(questId + ".item", quest.getItem().name());
                playerQuestSection.set(questId + ".amount", amount);
                playerQuestSection.set(questId + ".progress", false);
                savePlayerQuests();
            } else {
                player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
            }
        } else {
            player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }
//    private void saveQuestList() {
//        // 퀘스트 목록을 quests.yml 파일에 저장합니다
//        questConfig.set("quests", null);
//        for (Quest quest : questList) {
//            ConfigurationSection questSection = questConfig.createSection("quests." + quest.getName());
//            questSection.set("item", quest.getItem().name());
//            questSection.set("amount", quest.getAmount());
//        }
//
//        try {
//            questConfig.save(questFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    private void loadQuestList() {
        // quests.yml 파일에서 퀘스트 목록을 로드합니다
        ConfigurationSection questsSection = questConfig.getConfigurationSection("quests");
        if (questsSection != null) {
            for (String questName : questsSection.getKeys(false)) {
                ConfigurationSection questSection = questsSection.getConfigurationSection(questName);
                if (questSection != null) {
                    String itemName = questSection.getString("item");
                    int amount = questSection.getInt("amount");
                    if (itemName != null) {
                        Material item = Material.getMaterial(itemName);
                        if (item != null) {
                            Quest quest = new Quest(questName, item, amount);
                            questList.add(quest);
                        }
                    }
                }
            }
        }
    }
    private void savePlayerQuests() {
        // 플레이어 퀘스트 진행 상황을 playerQuest.yml 파일에 저장합니다
        try {
            playerQuestConfig.save(playerQuestFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void playerQuestList(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, "퀘스트");

        UUID playerId = player.getUniqueId();
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        if (playerQuestSection != null) {
            for (String questId : playerQuestSection.getKeys(false)) {
                ConfigurationSection questSection = playerQuestSection.getConfigurationSection(questId);
                if (questSection != null) {
                    String questName = questSection.getString("quest");
                    String itemString = questSection.getString("item");
                    Material layeritemcount = Material.getMaterial(itemString);
                    int itemCount = countItems(player.getInventory(), layeritemcount);
                    int amount = questSection.getInt("amount");
                    boolean progress = questSection.getBoolean("progress");
                    String book = progress ? "ENCHANTED_BOOK" : "BOOK";
                    Material item = Material.getMaterial(book);
                    if (item != null) {
                        ItemStack questItem = new ItemStack(item, 1);
                        ItemMeta meta = questItem.getItemMeta();
                        String progressStat = progress ? "§6[완료]" : "§a[진행중]";
                        String progresscount = progress ? "§7§m" : "§f";
                        meta.setDisplayName("§a[ 일일 퀘스트 ] §f"+questName + " " + progressStat);
                        meta.setLore(Arrays.asList(progresscount+itemCount+ " / "+amount));
                        questItem.setItemMeta(meta);
                        setItem(inventory, inventory.firstEmpty(), questItem);
                    }
                }
            }
        }
        player.openInventory(inventory);
    }
    private void setItem(Inventory inventory, int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }
    private int countItems(Inventory inventory, Material itemType) {
        int count = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && itemStack.getType() == itemType) {
                count += itemStack.getAmount();
            }
        }
        return count;
    }
    @EventHandler
    public void ShopMenuInventory(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null) return;

        // 메뉴 상점
        if (event.getView().getTitle().equalsIgnoreCase("[ 갈치의 놀이터 ] 메뉴")) {
            event.setCancelled(true); // 이벤트 취소하여 아이템을 메뉴로 옮기지 못하도록 함
            if (inventory != null && inventory.getType() == InventoryType.PLAYER) {
                // 클릭한 인벤토리가 플레이어 인벤토리인 경우
                event.setCancelled(true); // 이벤트 취소하여 아이템을 메뉴로 옮기지 못하도록 함
            }
            if (event.getSlot() == 15){
                playerQuestList(player);
            }
        }
    }
    public void resetPlayerQuestsAtSpecificTime() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // 현재 시간을 가져옵니다.
            Date now = new Date();

            // 현재 시간을 분 단위로 변환합니다.
            SimpleDateFormat format = new SimpleDateFormat("HH:mm");
            String currentTime = format.format(now);
            int currentMinute = Integer.parseInt(currentTime.split(":")[1]);

            // 00:00에서 00:01 사이인지 확인합니다.
            if (currentMinute >= 0 && currentMinute <= 1) {
                resetPlayerQuestFile();
            }
        }, 0L, 20 * 60L); // 1분(60초)마다 작업을 실행합니다.
    }






    public void resetPlayerQuestFile() {
        File playerQuestFile = new File(getDataFolder(), "playerQuest.yml");
        if (playerQuestFile.exists()) {
            playerQuestFile.delete();
        }

        try {
            playerQuestFile.createNewFile();
            playerQuestConfig = YamlConfiguration.loadConfiguration(playerQuestFile);
            savePlayerQuests();
            // 파일 재생성 후 초기 설정이 필요한 경우 여기에서 설정 작업을 수행할 수 있습니다.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
