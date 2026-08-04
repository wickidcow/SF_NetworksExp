package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.utils.Debug;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** SQLite persistence for Networks drawers. All runtime calls are serialized by {@link QueryQueue}. */
@SuppressWarnings("SqlSourceToSinkFlow")
public final class DataSource implements AutoCloseable {

    private static final String ITEM_ID_KEY = "NEXT_ITEM_ID";
    private static final String CONTAINER_ID_KEY = "NEXT_CONTAINER_ID";

    private final @NotNull Logger logger;
    private final @NotNull Map<Integer, ItemStack> itemMap = new ConcurrentHashMap<>();
    private final @NotNull Map<String, String> environment = new ConcurrentHashMap<>();
    private Connection connection;
    private int nextContainerId;
    private int nextItemId;

    public DataSource() throws ClassNotFoundException, SQLException {
        logger = Networks.getInstance().getLogger();
        connect();
        createTable();
        loadItemMap();
        loadEnvironment();
        initCounters();
    }

    @SuppressWarnings("deprecation")
    public static @NotNull String getBase64String(@NotNull ItemStack item) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(stream)) {
            output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encodeToString(stream.toByteArray());
        }
    }

    @SuppressWarnings("deprecation")
    public static @NotNull ItemStack getItemStack(@NotNull String base64) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getMimeDecoder().decode(base64);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream input = new BukkitObjectInputStream(stream)) {
            Object value = input.readObject();
            if (!(value instanceof ItemStack itemStack)) {
                throw new IOException("Stored Networks item is not an ItemStack");
            }
            return itemStack;
        }
    }

    void saveNewStorageData(@NotNull StorageUnitData storageData) {
        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "INSERT INTO " + DataTables.CONTAINER + " VALUES(?,?,?,?,?);";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, storageData.getId());
                statement.setString(2, storageData.getOwner().getUniqueId().toString());
                statement.setInt(3, storageData.getSizeType().ordinal());
                statement.setInt(4, storageData.isPlaced() ? 1 : 0);
                statement.setString(5, DataStorage.formatLocation(storageData.getLastLocation()));
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-saving-new-data"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    synchronized int getNextContainerId() {
        int id = nextContainerId++;
        persistCounter(CONTAINER_ID_KEY, nextContainerId);
        return id;
    }

    @Nullable
    StorageUnitData getStorageData(int id) {
        String sql = "SELECT * FROM " + DataTables.CONTAINER + " WHERE ContainerID = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                Location location = parseLocation(result.getString("LastLocation"));
                StorageUnitType[] types = StorageUnitType.values();
                int ordinal = Math.floorMod(result.getInt("SizeType"), types.length);
                return new StorageUnitData(
                    result.getInt("ContainerID"),
                    result.getString("PlayerUUID"),
                    types[ordinal],
                    result.getBoolean("IsPlaced"),
                    location,
                    loadStoredItems(id));
            }
        } catch (SQLException exception) {
            logger.warning(Lang.getString("messages.data-saving.error-occurred-when-loading-data"));
            Debug.trace(exception);
            return null;
        }
    }

    synchronized int getItemId(@NotNull ItemStack item) {
        ItemStack clone = item.clone();
        ItemStackWrapper wrapper = ItemStackWrapper.wrap(clone);
        for (Map.Entry<Integer, ItemStack> entry : itemMap.entrySet()) {
            if (StackUtils.itemsMatch(entry.getValue(), wrapper)) {
                return entry.getKey();
            }
        }

        int id = nextItemId++;
        // Publish immediately so simultaneous deposits cannot assign multiple IDs to the same item.
        itemMap.put(id, clone);
        persistCounter(ITEM_ID_KEY, nextItemId);

        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "INSERT INTO " + DataTables.ITEM_STACK + " VALUES (?,?);";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                statement.setString(2, getBase64String(clone));
                statement.executeUpdate();
                return true;
            } catch (SQLException | IOException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-saving-itemstack"));
                Debug.trace(exception);
                return false;
            }
        });
        return id;
    }

    void updateContainer(int id, @NotNull String key, @NotNull String value) {
        if (!key.equals("IsPlaced") && !key.equals("SizeType") && !key.equals("LastLocation")) {
            throw new IllegalArgumentException("Unsupported drawer column: " + key);
        }
        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "UPDATE " + DataTables.CONTAINER + " SET " + key + " = ? WHERE ContainerID = ?;";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                statement.setInt(2, id);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-updating-container-data"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    void addStoredItem(int containerId, int itemId, int amount) {
        if (amount <= 0) {
            return;
        }
        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "INSERT INTO " + DataTables.ITEM_STORED
                + " (ContainerID, ItemID, Amount) VALUES(?,?,?) "
                + "ON CONFLICT(ContainerID, ItemID) DO UPDATE SET "
                + "Amount = CAST(Amount AS INTEGER) + CAST(excluded.Amount AS INTEGER);";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, containerId);
                statement.setInt(2, itemId);
                statement.setInt(3, amount);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-updating-storage"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    void updateItemAmount(int containerId, int itemId, int amount) {
        if ((NetworksDrawer.isLocked(containerId) && amount < 0)
            || (!NetworksDrawer.isLocked(containerId) && amount <= 0)) {
            deleteStoredItem(containerId, itemId);
            return;
        }

        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "INSERT INTO " + DataTables.ITEM_STORED
                + " (Amount, ContainerID, ItemID) VALUES(?,?,?) "
                + "ON CONFLICT(ContainerID, ItemID) DO UPDATE SET Amount = excluded.Amount;";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, amount);
                statement.setInt(2, containerId);
                statement.setInt(3, itemId);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-updating-storage"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    void deleteStoredItem(int containerId, int itemId) {
        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "DELETE FROM " + DataTables.ITEM_STORED + " WHERE ContainerID = ? AND ItemID = ?;";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, containerId);
                statement.setInt(2, itemId);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-updating-storage"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    int getIdFromLocation(@NotNull Location location) {
        String sql = "SELECT ContainerID FROM " + DataTables.CONTAINER
            + " WHERE IsPlaced = 1 AND LastLocation = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DataStorage.formatLocation(location));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : -1;
            }
        } catch (SQLException exception) {
            logger.warning(Lang.getString("messages.data-saving.error-occurred-when-fixing-data"));
            Debug.trace(exception);
            return -1;
        }
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.warning("Failed to close the Networks drawer database cleanly.");
            Debug.trace(exception);
        } finally {
            connection = null;
        }
    }

    private void connect() throws SQLException, ClassNotFoundException {
        File dataFolder = Networks.getInstance().getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException(
                Lang.getString("messages.data-saving.error-occurred-when-creating-data-folder"));
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "CargoStorageUnits.db"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            statement.execute("PRAGMA busy_timeout = 10000;");
            statement.execute("PRAGMA journal_mode = WAL;");
            statement.execute("PRAGMA synchronous = NORMAL;");
        }
    }

    private void createTable() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute(DataTables.CONTAINER_CREATION);
            statement.execute(DataTables.ITEM_STACK_CREATION);
            statement.execute(DataTables.ITEM_STORED_CREATION);
            statement.execute(DataTables.ENVIRONMENT_CREATION);

            if (!hasIndex(DataTables.ITEM_STORED_UNIQUE_INDEX)) {
                // Older Networks builds could write duplicate rows for one container/item pair.
                // Merge them transactionally before adding the uniqueness guarantee.
                statement.execute("DROP TABLE IF EXISTS Networks_ItemStored_Merged;");
                statement.execute("CREATE TEMP TABLE Networks_ItemStored_Merged AS "
                    + "SELECT ContainerID, ItemID, SUM(CAST(Amount AS INTEGER)) AS Amount "
                    + "FROM " + DataTables.ITEM_STORED + " GROUP BY ContainerID, ItemID;");
                statement.execute("DELETE FROM " + DataTables.ITEM_STORED + ";");
                statement.execute("INSERT INTO " + DataTables.ITEM_STORED
                    + " (ContainerID, ItemID, Amount) SELECT ContainerID, ItemID, Amount "
                    + "FROM Networks_ItemStored_Merged;");
                statement.execute("DROP TABLE Networks_ItemStored_Merged;");
            }

            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + DataTables.ITEM_STORED_UNIQUE_INDEX
                + " ON " + DataTables.ITEM_STORED + " (ContainerID, ItemID);");
            statement.execute("CREATE INDEX IF NOT EXISTS " + DataTables.CONTAINER_LOCATION_INDEX
                + " ON " + DataTables.CONTAINER + " (IsPlaced, LastLocation);");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean hasIndex(@NotNull String indexName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ? LIMIT 1;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, indexName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void loadItemMap() {
        executeQuery("SELECT Item, ItemID FROM " + DataTables.ITEM_STACK + ";", result -> {
            try {
                while (result.next()) {
                    itemMap.put(result.getInt("ItemID"), getItemStack(result.getString("Item")));
                }
            } catch (SQLException | IOException | ClassNotFoundException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-loading-itemstack"));
                Debug.trace(exception);
            }
        });
    }

    private void loadEnvironment() {
        executeQuery("SELECT VarName, VarValue FROM " + DataTables.ENVIRONMENT + ";", result -> {
            try {
                while (result.next()) {
                    environment.put(result.getString(1), result.getString(2));
                }
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-loading-environment-var"));
                Debug.trace(exception);
            }
        });
    }

    private void initCounters() {
        nextItemId = Math.max(parseCounter(ITEM_ID_KEY), highestUsedId(DataTables.ITEM_STACK, "ItemID") + 1);
        nextContainerId = Math.max(parseCounter(CONTAINER_ID_KEY), highestUsedId(DataTables.CONTAINER, "ContainerID") + 1);
        persistCounter(ITEM_ID_KEY, nextItemId);
        persistCounter(CONTAINER_ID_KEY, nextContainerId);
    }

    private int highestUsedId(@NotNull String table, @NotNull String column) {
        String sql = "SELECT MAX(CAST(" + column + " AS INTEGER)) FROM " + table + ';';
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : -1;
        } catch (SQLException exception) {
            logger.warning("Could not verify the Networks database counter for " + table + '.');
            Debug.trace(exception);
            return -1;
        }
    }

    private int parseCounter(String key) {
        String value = environment.get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            logger.warning("Invalid Networks database counter " + key + "='" + value + "'; resetting to 0.");
            return 0;
        }
    }

    private void persistCounter(@NotNull String key, int value) {
        environment.put(key, Integer.toString(value));
        Networks.getQueryQueue().scheduleUpdate(() -> {
            String sql = "INSERT INTO " + DataTables.ENVIRONMENT
                + " (VarName, VarValue) VALUES (?, ?) ON CONFLICT(VarName) DO UPDATE SET VarValue = excluded.VarValue;";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key);
                statement.setInt(2, value);
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                logger.warning(Lang.getString("messages.data-saving.error-occurred-when-updating-environment-var"));
                Debug.trace(exception);
                return false;
            }
        });
    }

    private @NotNull ConcurrentHashMap<Integer, ItemContainer> loadStoredItems(int containerId) {
        ConcurrentHashMap<Integer, ItemContainer> stored = new ConcurrentHashMap<>();
        String sql = "SELECT ItemID, Amount FROM " + DataTables.ITEM_STORED + " WHERE ContainerID = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, containerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int itemId = result.getInt("ItemID");
                    ItemStack item = itemMap.get(itemId);
                    if (item != null) {
                        stored.put(itemId, new ItemContainer(itemId, item, result.getInt("Amount")));
                    }
                }
            }
        } catch (SQLException exception) {
            logger.warning(Lang.getString("messages.data-saving.error-occurred-when-loading-storage"));
            Debug.trace(exception);
        }
        return stored;
    }

    private @Nullable Location parseLocation(@Nullable String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        String[] parts = serialized.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return new Location(
                world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void executeQuery(@NotNull String sql, @NotNull Consumer<ResultSet> usage) {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            usage.accept(result);
        } catch (SQLException exception) {
            logger.warning(Lang.getString("messages.data-saving.error-occurred-when-executing-query"));
            Debug.trace(exception);
        }
    }
}
