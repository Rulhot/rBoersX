package ru.rulhot.rBoersX.database.model;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@DatabaseTable(tableName = "rboersx_boers")
public final class BoerRecord {

    private static final int UUID_LENGTH = 36;
    private static final int NAME_LENGTH = 64;

    @DatabaseField(generatedId = true, columnName = "id")
    private long id;

    @DatabaseField(columnName = "world", canBeNull = false, width = UUID_LENGTH)
    private @Nullable String world;

    @DatabaseField(columnName = "x", canBeNull = false)
    private int x;

    @DatabaseField(columnName = "y", canBeNull = false)
    private int y;

    @DatabaseField(columnName = "z", canBeNull = false)
    private int z;

    @DatabaseField(columnName = "type", canBeNull = false, width = NAME_LENGTH)
    private @Nullable String type;

    @DatabaseField(columnName = "owner", canBeNull = false, width = UUID_LENGTH)
    private @Nullable String owner;

    @DatabaseField(columnName = "owner_name", canBeNull = false, width = NAME_LENGTH)
    private @Nullable String ownerName;

    @DatabaseField(columnName = "facing", width = NAME_LENGTH)
    private @Nullable String facing;

    @DatabaseField(columnName = "forward_zone", canBeNull = false)
    private boolean forwardZone;

    @DatabaseField(columnName = "fuel_millis", canBeNull = false)
    private long fuelMillis;

    @DatabaseField(columnName = "status", canBeNull = false, width = NAME_LENGTH)
    private @Nullable String status;

    @DatabaseField(columnName = "cursor_index", canBeNull = false)
    private long cursor;

    @DatabaseField(columnName = "mined", canBeNull = false)
    private long mined;

    @DatabaseField(columnName = "mine_other", canBeNull = false)
    private boolean mineOther;

    @DatabaseField(columnName = "disabled_blocks", dataType = DataType.LONG_STRING)
    private @Nullable String disabledBlocks;

    @DatabaseField(columnName = "fuel_items", dataType = DataType.BYTE_ARRAY)
    private byte @Nullable [] fuelItems;

    @DatabaseField(columnName = "storage_items", dataType = DataType.BYTE_ARRAY)
    private byte @Nullable [] storageItems;

    @DatabaseField(columnName = "storage_counts", dataType = DataType.LONG_STRING)
    private @Nullable String storageCounts;
}
