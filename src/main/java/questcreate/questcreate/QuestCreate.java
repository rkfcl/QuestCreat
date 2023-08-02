package questcreate.questcreate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    private List<Quest> weeklyQuestList;
    private static QuestCreate instance;
    private Calendar serverShutdownTime;

    public static QuestCreate getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        getLogger().info("퀘스트 플러그인 작동");

        questList = new ArrayList<>();
        weeklyQuestList = new ArrayList<>();
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

        // 서버 종료 시간 가져오기 (밀리초 단위로 변환된 시간을 다시 일반 시간 형식으로 변환)
        long shutdownTimeMillis = playerQuestConfig.getLong("server_shutdown_time", 0L);
        if (shutdownTimeMillis > 0L) {
            serverShutdownTime = Calendar.getInstance();
            serverShutdownTime.setTimeInMillis(shutdownTimeMillis);

            // 서버 시작 시와 종료 시간이 다른 날짜인지, 시간만 바뀌었는지 확인하여 퀘스트 초기화
            Calendar currentTime = Calendar.getInstance();
            if (currentTime.get(Calendar.YEAR) != serverShutdownTime.get(Calendar.YEAR)
                    || currentTime.get(Calendar.DAY_OF_YEAR) != serverShutdownTime.get(Calendar.DAY_OF_YEAR)) {
                resetAllPlayersWeekly_QuestFile();
                resetAllPlayersDaily_QuestFile();
            } else if (currentTime.get(Calendar.HOUR_OF_DAY) != serverShutdownTime.get(Calendar.HOUR_OF_DAY)) {
                resetAllPlayersDaily_QuestFile();
            }
        }

        resetPlayerQuestsAtSpecificTime();
        // 플러그인 활성화 시에 실행될 로직
        getServer().getPluginManager().registerEvents(this, this);

        // 퀘스트 목록 초기화
        loadQuestList();
        instance = this;
    }



    @Override
    public void onDisable() {
        // 서버 종료 시점의 시간 저장 (밀리초 단위로 변환)
        long serverShutdownTime = System.currentTimeMillis();
        playerQuestConfig.set("server_shutdown_time", serverShutdownTime);

        // 플러그인 비활성화 시에 실행될 로직
        getLogger().info("퀘스트 플러그인 종료");
        savePlayerQuests();
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("일일퀘스트초기화")) {
            resetAllPlayersDaily_QuestFile();
            return true;
        }else if (command.getName().equalsIgnoreCase("주간퀘스트초기화")) {
            resetAllPlayersWeekly_QuestFile();
            return true;
        }
        return false;
    }
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            String villagerName = villager.getCustomName();

            if (villagerName != null && villagerName.equals("일일퀘스트")) {
                handleDailyQuestInteraction(player);
            } else if (villagerName != null && villagerName.equals("주간퀘스트")) {
                handleWeeklyQuestInteraction(player);
            }
        }
    }

    private void handleDailyQuestInteraction(Player player) {
        UUID playerId = player.getUniqueId();
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        boolean check = true;
        if (playerQuestSection != null) {
            int currentDailyQuestCount = 0;

            for (String questId : playerQuestSection.getKeys(false)) {
                if (questId.startsWith("daily_quest")) {
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
                                sendTitle(player, "퀘스트 완료", "§a[ 일일 퀘스트 ] §f" + questName + " §a완료!", 10, 70, 10);
                                check=false;
                                break;
                            }
                        }
                    }
                    currentDailyQuestCount++;
                }
            }

            if (currentDailyQuestCount >= 3) {
                player.sendMessage("§c일일퀘스트는 하루에 3개까지 받을 수 있습니다.");
                return;
            }
        }

        // 설정된 퀘스트 목록에서 랜덤하게 퀘스트를 선택합니다
        if (!questList.isEmpty()&&check) {
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
                    player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
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
                String questId = "daily_quest" + (playerQuestSection.getKeys(false).size() + 1);
                playerQuestSection.set(questId + ".quest", quest.getName());
                playerQuestSection.set(questId + ".item", quest.getItem().name());
                playerQuestSection.set(questId + ".amount", amount);
                playerQuestSection.set(questId + ".progress", false);
                savePlayerQuests();
            } else {
                player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
            }
        }
    }


    private void handleWeeklyQuestInteraction(Player player) {
        UUID playerId = player.getUniqueId();
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        boolean check = true;
        if (playerQuestSection != null) {
            int currentWeeklyQuestCount = 0;

            for (String questId : playerQuestSection.getKeys(false)) {
                if (questId.startsWith("weekly_quest")) {
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
                                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "iagive " + player.getName() + " protectblock:rkfcl_coin " + 5);
                                questSection.set("progress", true);
                                savePlayerQuests();
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                sendTitle(player, "퀘스트 완료", "§a[ 주간 퀘스트 ] §f" + questName + " §a완료!", 10, 70, 10);
                                check=false;
                                break;
                            }
                        }
                    }
                    currentWeeklyQuestCount++;
                }
                
            }

            if (currentWeeklyQuestCount >= 3) {
                player.sendMessage("§c주간퀘스트는 주에 3개까지 받을 수 있습니다.");
                return;
            }
        }

        // 설정된 주간 퀘스트 목록에서 랜덤하게 퀘스트를 선택합니다
        if (!weeklyQuestList.isEmpty()&&check) {
            Random random = new Random();
            int randomIndex = random.nextInt(weeklyQuestList.size());
            Quest quest = weeklyQuestList.get(randomIndex);
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
                    player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
                    return;
                }

                // 퀘스트 받는 소리
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                int amount = quest.getAmount();
                player.sendMessage("§a퀘스트를 받았습니다!");
                player.sendMessage("§a[ 주간 퀘스트 ] §f" + quest.getName() + " " + amount + "개를 구해와");
                player.sendMessage("§7주간퀘스트는 주에 3개까지 받을 수 있습니다.");

                // 플레이어 퀘스트 진행 상황을 저장합니다
                playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
                if (playerQuestSection == null) {
                    playerQuestSection = playerQuestConfig.createSection(playerId.toString());
                }
                String questId = "weekly_quest" + (playerQuestSection.getKeys(false).size() + 1);
                playerQuestSection.set(questId + ".quest", quest.getName());
                playerQuestSection.set(questId + ".item", quest.getItem().name());
                playerQuestSection.set(questId + ".amount", amount);
                playerQuestSection.set(questId + ".progress", false);
                savePlayerQuests();
            } else {
                player.sendMessage("§c퀘스트를 준비 중입니다. 잠시 후 다시 시도해주세요.");
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

    private void loadQuestList() {
        ConfigurationSection dailyQuestsSection = questConfig.getConfigurationSection("daily_quests");
        if (dailyQuestsSection != null) {
            for (String questName : dailyQuestsSection.getKeys(false)) {
                ConfigurationSection questSection = dailyQuestsSection.getConfigurationSection(questName);
                if (questSection != null) {
                    String itemName = questSection.getString("item");
                    int amount = questSection.getInt("amount");
                    if (itemName != null) {
                        Material item = Material.getMaterial(itemName);
                        if (item != null) {
                            // 일일 퀘스트로 추가
                            Quest dailyQuest = new Quest(questName, item, amount);
                            questList.add(dailyQuest);
                        }
                    }
                }
            }
        }

        ConfigurationSection weeklyQuestsSection = questConfig.getConfigurationSection("weekly_quests");
        if (weeklyQuestsSection != null) {
            for (String questName : weeklyQuestsSection.getKeys(false)) {
                ConfigurationSection questSection = weeklyQuestsSection.getConfigurationSection(questName);
                if (questSection != null) {
                    String itemName = questSection.getString("item");
                    int amount = questSection.getInt("amount");
                    if (itemName != null) {
                        Material item = Material.getMaterial(itemName);
                        if (item != null) {
                            // 주간 퀘스트로 추가
                            Quest weeklyQuest = new Quest(questName, item, amount);
                            weeklyQuestList.add(weeklyQuest);
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
            List<String> dailyQuestIds = new ArrayList<>();
            List<String> weeklyQuestIds = new ArrayList<>();

            for (String questId : playerQuestSection.getKeys(false)) {
                if (questId.startsWith("daily_quest")) {
                    dailyQuestIds.add(questId);
                } else if (questId.startsWith("weekly_quest")) {
                    weeklyQuestIds.add(questId);
                }
            }

            // 일일 퀘스트를 먼저 추가
            for (String questId : dailyQuestIds) {
                ConfigurationSection questSection = playerQuestSection.getConfigurationSection(questId);
                if (questSection != null) {
                    String questName = questSection.getString("quest");
                    String itemString = questSection.getString("item");
                    Material layeritemcount = Material.getMaterial(itemString);
                    int itemCount = countItems(player.getInventory(), layeritemcount);
                    int amount = questSection.getInt("amount");
                    boolean progress = questSection.getBoolean("progress");
                    Material item = Material.getMaterial(progress ? "ENCHANTED_BOOK" : "BOOK");
                    if (item != null) {
                        ItemStack questItem = new ItemStack(item, 1);
                        ItemMeta meta = questItem.getItemMeta();
                        String progressStat = progress ? "§6[완료]" : "§a[진행중]";
                        String progressCount = progress ? "§7§m" : "§f";
                        meta.setDisplayName("§a[ 일일 퀘스트 ] §f" + questName + " " + progressStat);
                        meta.setLore(Collections.singletonList(progressCount + itemCount + " / " + amount));
                        questItem.setItemMeta(meta);
                        setItem(inventory, inventory.firstEmpty(), questItem);
                    }
                }
            }

            // 주간 퀘스트를 추가
            for (String questId : weeklyQuestIds) {
                ConfigurationSection questSection = playerQuestSection.getConfigurationSection(questId);
                if (questSection != null) {
                    String questName = questSection.getString("quest");
                    String itemString = questSection.getString("item");
                    Material layeritemcount = Material.getMaterial(itemString);
                    int itemCount = countItems(player.getInventory(), layeritemcount);
                    int amount = questSection.getInt("amount");
                    boolean progress = questSection.getBoolean("progress");
                    Material item = Material.getMaterial(progress ? "ENCHANTED_BOOK" : "BOOK");
                    if (item != null) {
                        ItemStack questItem = new ItemStack(item, 1);
                        ItemMeta meta = questItem.getItemMeta();
                        String progressStat = progress ? "§6[완료]" : "§a[진행중]";
                        String progressCount = progress ? "§7§m" : "§f";
                        meta.setDisplayName("§a[ 주간 퀘스트 ] §f" + questName + " " + progressStat);
                        meta.setLore(Collections.singletonList(progressCount + itemCount + " / " + amount));
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
        for (ItemStack itemStack : inventory.getStorageContents()) {
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
            if (event.getSlot() == 15) {
                playerQuestList(player);
            }
        }
        if (event.getView().getTitle().equalsIgnoreCase("퀘스트")) {
            event.setCancelled(true); // 이벤트 취소하여 아이템을 메뉴로 옮기지 못하도록 함
        }
    }

    public void resetPlayerQuestsAtSpecificTime() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // 현재 시간을 가져옵니다.
            Date now = new Date();

            // 현재 시간을 분 단위로 변환합니다.
            SimpleDateFormat format = new SimpleDateFormat("HH:mm");
            String currentTime = format.format(now);
            int currentHour = Integer.parseInt(currentTime.split(":")[0]);
            int currentMinute = Integer.parseInt(currentTime.split(":")[1]);

            // 00:00에서 00:01 사이인지 확인합니다.
            if (currentMinute >= 0 && currentMinute < 1) {
                resetAllPlayersDaily_QuestFile();
            }
            if ((currentMinute >= 0 && currentMinute < 1)&&currentHour == 0) {
                resetAllPlayersWeekly_QuestFile();
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
    public void resetAllPlayersDaily_QuestFile() {
        // 모든 플레이어에 대해 일일 퀘스트 삭제
        for (String playerId : playerQuestConfig.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(playerId);
            } catch (IllegalArgumentException e) {
                // 유효하지 않은 UUID 문자열일 경우 건너뜁니다.
                continue;
            }
            resetPlayerDaily_QuestFile(playerQuestConfig, uuid);
        }
    }

    public void resetPlayerDaily_QuestFile(FileConfiguration playerQuestConfig, UUID playerId) {
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        if (playerQuestSection != null) {
            Set<String> keysToRemove = new HashSet<>();
            for (String questId : playerQuestSection.getKeys(false)) {
                if (questId.startsWith("daily_quest")) {
                    keysToRemove.add(questId);
                }
            }
            for (String key : keysToRemove) {
                playerQuestSection.set(key, null);
            }
            savePlayerQuests();
        }
    }

    public void resetAllPlayersWeekly_QuestFile() {
        // 모든 플레이어에 대해 주간 퀘스트 삭제
        for (String playerId : playerQuestConfig.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(playerId);
            } catch (IllegalArgumentException e) {
                // 유효하지 않은 UUID 문자열일 경우 건너뜁니다.
                continue;
            }
            resetPlayerDWeekly_QuestFile(playerQuestConfig, uuid);
        }
    }

    public void resetPlayerDWeekly_QuestFile(FileConfiguration playerQuestConfig, UUID playerId) {
        ConfigurationSection playerQuestSection = playerQuestConfig.getConfigurationSection(playerId.toString());
        if (playerQuestSection != null) {
            Set<String> keysToRemove = new HashSet<>();
            for (String questId : playerQuestSection.getKeys(false)) {
                if (questId.startsWith("weekly_quest")) {
                    keysToRemove.add(questId);
                }
            }
            for (String key : keysToRemove) {
                playerQuestSection.set(key, null);
            }
            savePlayerQuests();
        }
    }


}
