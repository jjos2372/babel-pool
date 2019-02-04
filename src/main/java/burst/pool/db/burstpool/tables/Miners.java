/*
 * This file is generated by jOOQ.
 */
package burst.pool.db.burstpool.tables;


import burst.pool.db.burstpool.Burstpool;
import burst.pool.db.burstpool.Indexes;
import burst.pool.db.burstpool.Keys;
import burst.pool.db.burstpool.tables.records.MinersRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.9"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Miners extends TableImpl<MinersRecord> {

    private static final long serialVersionUID = 1529150527;

    /**
     * The reference instance of <code>BURSTPOOL.MINERS</code>
     */
    public static final Miners MINERS = new Miners();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<MinersRecord> getRecordType() {
        return MinersRecord.class;
    }

    /**
     * The column <code>BURSTPOOL.MINERS.DB_ID</code>.
     */
    public final TableField<MinersRecord, Long> DB_ID = createField("DB_ID", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.ACCOUNT_ID</code>.
     */
    public final TableField<MinersRecord, Long> ACCOUNT_ID = createField("ACCOUNT_ID", org.jooq.impl.SQLDataType.BIGINT, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.PENDING_BALANCE</code>.
     */
    public final TableField<MinersRecord, Double> PENDING_BALANCE = createField("PENDING_BALANCE", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.ESTIMATED_CAPACITY</code>.
     */
    public final TableField<MinersRecord, Double> ESTIMATED_CAPACITY = createField("ESTIMATED_CAPACITY", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.SHARE</code>.
     */
    public final TableField<MinersRecord, Double> SHARE = createField("SHARE", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.HITSUM</code>.
     */
    public final TableField<MinersRecord, Double> HITSUM = createField("HITSUM", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.MINIMUM_PAYOUT</code>.
     */
    public final TableField<MinersRecord, Double> MINIMUM_PAYOUT = createField("MINIMUM_PAYOUT", org.jooq.impl.SQLDataType.DOUBLE, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.NAME</code>.
     */
    public final TableField<MinersRecord, String> NAME = createField("NAME", org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * The column <code>BURSTPOOL.MINERS.USER_AGENT</code>.
     */
    public final TableField<MinersRecord, String> USER_AGENT = createField("USER_AGENT", org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * Create a <code>BURSTPOOL.MINERS</code> table reference
     */
    public Miners() {
        this(DSL.name("MINERS"), null);
    }

    /**
     * Create an aliased <code>BURSTPOOL.MINERS</code> table reference
     */
    public Miners(String alias) {
        this(DSL.name(alias), MINERS);
    }

    /**
     * Create an aliased <code>BURSTPOOL.MINERS</code> table reference
     */
    public Miners(Name alias) {
        this(alias, MINERS);
    }

    private Miners(Name alias, Table<MinersRecord> aliased) {
        this(alias, aliased, null);
    }

    private Miners(Name alias, Table<MinersRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> Miners(Table<O> child, ForeignKey<O, MinersRecord> key) {
        super(child, key, MINERS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return Burstpool.BURSTPOOL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.PRIMARY_KEY_8);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<MinersRecord, Long> getIdentity() {
        return Keys.IDENTITY_MINERS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<MinersRecord> getPrimaryKey() {
        return Keys.CONSTRAINT_8;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<MinersRecord>> getKeys() {
        return Arrays.<UniqueKey<MinersRecord>>asList(Keys.CONSTRAINT_8);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Miners as(String alias) {
        return new Miners(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Miners as(Name alias) {
        return new Miners(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Miners rename(String name) {
        return new Miners(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Miners rename(Name name) {
        return new Miners(name, null);
    }
}
