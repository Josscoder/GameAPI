/*
 * Copyright 2021 Josscoder
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.josscoder.gameapi;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Event;
import cn.nukkit.event.Listener;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import java.io.File;
import java.time.Duration;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.josscoder.gameapi.command.*;
import net.josscoder.gameapi.customitem.factory.CustomItemFactory;
import net.josscoder.gameapi.customitem.listener.BlockListener;
import net.josscoder.gameapi.customitem.listener.InventoryListener;
import net.josscoder.gameapi.customitem.listener.ItemInteractListener;
import net.josscoder.gameapi.map.manager.GameMapManager;
import net.josscoder.gameapi.phase.base.LobbyCountdownPhase;
import net.josscoder.gameapi.phase.base.LobbyWaitingPhase;
import net.josscoder.gameapi.user.listener.UserEventListener;
import net.josscoder.gameapi.util.entity.CustomItemFirework;
import net.josscoder.gameapi.command.*;
import net.josscoder.gameapi.customitem.CustomItem;
import net.josscoder.gameapi.map.WaitingRoomMap;
import net.josscoder.gameapi.phase.GamePhase;
import net.josscoder.gameapi.phase.PhaseSeries;
import net.josscoder.gameapi.phase.base.PreGamePhase;
import net.josscoder.gameapi.user.factory.UserFactory;
import net.josscoder.gameapi.util.ZipUtils;
import org.citizen.CitizenLibrary;

@Getter
public abstract class Game extends PluginBase {

  @Setter
  protected boolean developmentMode = false;

  @Setter
  protected WaitingRoomMap waitingRoomMap;

  @Setter
  protected int defaultGamemode = 0;

  @Setter
  protected boolean started = false;

  @Setter
  protected boolean mapVoteFinished = false;

  @Setter
  protected int minPlayers = 2;

  @Setter
  protected int maxPlayers = 12;

  @Setter
  protected boolean moveInPreGame = false;

  protected List<String> tips;

  protected PhaseSeries phaseSeries = null;

  protected UserFactory userFactory;

  protected CustomItemFactory customItemFactory;

  protected GameMapManager gameMapManager;

  private CitizenLibrary citizenLibrary;

  @Setter
  protected Map<Integer, CustomItem> waitingLobbyItems;

  @Setter
  protected Map<Integer, CustomItem> spectatorItems;

  public abstract String getId();

  public abstract String getGameName();

  public abstract String getInstruction();

  public abstract void init();

  public abstract void close();

  @SneakyThrows
  @Override
  public void onEnable() {
    super.onEnable();

    tips = new ArrayList<>();

    waitingLobbyItems = new HashMap<>();

    spectatorItems = new HashMap<>();

    userFactory = new UserFactory(this);

    gameMapManager = new GameMapManager(this);

    citizenLibrary = new CitizenLibrary(this);

    registerListener(
      new UserEventListener(this),
      new BlockListener(this),
      new ItemInteractListener(this),
      new InventoryListener(this)
    );

    registerCommand(new VoteCommand(this), new TeleporterCommand(this));

    Entity.registerEntity("CustomItemFirework", CustomItemFirework.class, true);

    init();

    initDefaultItems();

    if (developmentMode) {
      getLogger().info(TextFormat.GOLD + "Development mode is enabled!");
      registerCommand(new SoundCommand(), new MyPositionCommand());
    }

    new Thread(
      () ->
        gameMapManager
          .getMaps()
          .keySet()
          .forEach(mapName -> reset(mapName, false))
    )
      .start();

    getLogger().info(TextFormat.GREEN + "This game has been enabled!");
  }

  protected void initDefaultItems() {
    CustomItem voteMapItem = new CustomItem(
      Item.get(ItemID.PAPER),
      "&r&bVote Map &7[Use]"
    );
    voteMapItem.setTransferable(false).addCommands("vote");
    waitingLobbyItems.put(0, voteMapItem);

    CustomItem exitItem = new CustomItem(
      Item.get(ItemID.DRAGON_BREATH),
      "&r&eBack to the game center &7[Use]"
    );
    exitItem
      .setTransferable(false)
      .setInteractHandler(((user, player) -> sendToTheGameCenter(player)));
    waitingLobbyItems.put(8, exitItem);

    CustomItem teleporterItem = new CustomItem(
      Item.get(Item.COMPASS),
      "&r&bTeleporter &7[Use]"
    );
    teleporterItem.setTransferable(false).addCommands("teleporter");
    spectatorItems.put(0, teleporterItem);

    CustomItem newGameItem = new CustomItem(
      Item.get(Item.HEART_OF_THE_SEA),
      "&r&aNew game &7[Use]"
    );
    newGameItem
      .setTransferable(false)
      .setInteractHandler(
        (user, player) -> {
          player.sendMessage(
            TextFormat.colorize("&l&b»&r&7 We will find a new game shortly...")
          );
          searchNewGameFor(player);
        }
      );
    spectatorItems.put(7, newGameItem);
  }

  public boolean isFull() {
    return getServer().getOnlinePlayers().size() >= maxPlayers;
  }

  public boolean isAvailable() {
    return !isFull() && !started;
  }

  public void unregisterAllCommands() {
    getServer()
      .getCommandMap()
      .getCommands()
      .values()
      .forEach(this::unregisterCommand);
  }

  protected List<GamePhase> createPreGamePhase() {
    return createPreGamePhase(Duration.ofSeconds(20));
  }

  protected List<GamePhase> createPreGamePhase(
    Duration lobbyCountdownDuration
  ) {
    return createPreGamePhase(lobbyCountdownDuration, 10);
  }

  protected List<GamePhase> createPreGamePhase(
    Duration lobbyCountdownDuration,
    int preGameCountdown
  ) {
    return createPreGamePhase(
      lobbyCountdownDuration,
      preGameCountdown,
      Level.TIME_DAY
    );
  }

  protected List<GamePhase> createPreGamePhase(
    Duration lobbyCountdownDuration,
    int preGameCountdown,
    int mapTime
  ) {
    List<GamePhase> gamePhases = new LinkedList<>();
    gamePhases.add(new LobbyWaitingPhase(this));
    gamePhases.add(new LobbyCountdownPhase(this, lobbyCountdownDuration));
    gamePhases.add(new PreGamePhase(this, preGameCountdown, mapTime));

    return gamePhases;
  }

  protected void registerPhaseCommands() {
    if (phaseSeries == null) {
      return;
    }

    registerCommand(
      new SkipPhaseCommand(phaseSeries),
      new FreezePhasesCommand(phaseSeries),
      new UnfreezePhasesCommand(phaseSeries)
    );
  }

  public void unregisterCommand(Command command) {
    command.unregister(getServer().getCommandMap());
  }

  public void registerCommand(Command command) {
    getServer().getCommandMap().register(command.getName(), command);
  }

  public void registerCommand(Command... commands) {
    Arrays.stream(commands).forEach(this::registerCommand);
  }

  public void registerListener(Listener listener) {
    getServer().getPluginManager().registerEvents(listener, this);
  }

  public void registerListener(Listener... listeners) {
    Arrays.stream(listeners).forEach(this::registerListener);
  }

  public void addTip(String... tip) {
    Arrays.stream(tip).forEach(this::addTip);
  }

  public void addTip(String tip) {
    tips.add(tip);
  }

  public void callEvent(Event event) {
    getServer().getPluginManager().callEvent(event);
  }

  public void schedule(Runnable runnable, int delay) {
    getServer().getScheduler().scheduleDelayedTask(this, runnable, delay);
  }

  public void reset() {
    reset("", true);
  }

  public void reset(String mapName, boolean shutdown) {
    if (!mapName.isEmpty() && mapHasBackup(mapName)) {
      resetMapBackup(mapName);
    }

    if (shutdown) {
      schedule(
        () ->
          getServer()
            .forceShutdown("§cThe game: " + getId() + " has been reset!"),
        20 * 5
      );
    }
  }

  public String getWorldsFolder() {
    return getServer().getDataPath() + "/worlds/";
  }

  public String getBackupFolder() {
    return getServer().getDataPath() + "/backups/";
  }

  public void storeMapBackup(String mapName) {
    unloadMap(mapName);

    String backupFolder = getBackupFolder();

    File backupFile = new File(backupFolder);

    if (!backupFile.isDirectory()) {
      backupFile.mkdirs();
    }

    if (mapHasBackup(mapName)) {
      removeMapBackup(mapName);
    }

    try {
      ZipUtils.zip(
        getWorldsFolder() + mapName,
        backupFolder + mapName + ".zip"
      );
    } catch (Exception e) {
      getLogger().error(e.getMessage(), e);
    }
  }

  public void resetMapBackup(String mapName) {
    unloadMap(mapName);

    try {
      ZipUtils.unzip(getBackupFolder() + mapName + ".zip", getWorldsFolder());
    } catch (Exception e) {
      getLogger().error(e.getMessage(), e);
    }
  }

  public boolean mapHasBackup(String mapName) {
    return (new File(getBackupFolder() + mapName + ".zip")).exists();
  }

  public void removeMapBackup(String mapName) {
    (new File(getBackupFolder() + mapName + ".zip")).delete();
  }

  public void unloadMap(String mapName) {
    if (!getServer().isLevelLoaded(mapName)) {
      return;
    }

    Level level = getServer().getLevelByName(mapName);

    if (level == null) {
      return;
    }

    level.setAutoSave(false);

    getServer().unloadLevel(level);
  }

  public void searchNewGameFor(Player player) {
    //TODO: implement this
    player.sendMessage("Search a new game!");
  }

  public void searchNewGameFor(List<Player> players) {
    players.forEach(this::searchNewGameFor);
  }

  public void sendToTheGameCenter(Player player) {
    //TODO: implement this
    player.sendMessage("Send player to the game center!");
  }

  @Override
  public void onDisable() {
    super.onDisable();

    close();

    getLogger().info(TextFormat.RED + "This game has been disabled!");
  }
}
