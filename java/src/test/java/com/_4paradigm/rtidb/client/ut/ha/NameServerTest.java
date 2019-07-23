package com._4paradigm.rtidb.client.ut.ha;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com._4paradigm.rtidb.client.base.TestCaseBase;
import com._4paradigm.rtidb.client.ha.RTIDBClientConfig;
import com._4paradigm.rtidb.client.ha.TableHandler;
import com._4paradigm.rtidb.client.schema.ColumnDesc;
import com._4paradigm.rtidb.client.base.Config;
import com._4paradigm.rtidb.ns.NS;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com._4paradigm.rtidb.client.ha.impl.NameServerClientImpl;
import com._4paradigm.rtidb.ns.NS.PartitionMeta;
import com._4paradigm.rtidb.ns.NS.TableInfo;
import com._4paradigm.rtidb.ns.NS.TablePartition;


/**
 * 需要外部启动ns 环境
 *
 * @author wangtaize
 */
public class NameServerTest extends TestCaseBase {
    private static String zkEndpoints = Config.ZK_ENDPOINTS;
    private static String zkRootPath = Config.ZK_ROOT_PATH;
    private static String leaderPath = zkRootPath + "/leader";
    private static String[] nodes = Config.NODES;

    @BeforeClass
    public void setUp() {
        super.setUp();
    }

    @AfterClass
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void testInvalidZkInit() {
        try {
            NameServerClientImpl nsc = new NameServerClientImpl("xxxxx", "xxxx");
            nsc.init();
            Assert.assertTrue(false);
            nsc.close();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testInvalidEndpointInit() {
        try {
            NameServerClientImpl nsc = new NameServerClientImpl(zkEndpoints, "xxxx");
            nsc.init();
            Assert.assertTrue(false);
            nsc.close();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testNsInit() {
        try {
            NameServerClientImpl nsc = new NameServerClientImpl(zkEndpoints, leaderPath);
            nsc.init();
            Assert.assertTrue(true);
            nsc.close();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testNsInitByConfig() {
        Random rand = new Random(System.currentTimeMillis());
        String tname = rand.nextInt() + "tname";
        try {
            Assert.assertTrue(true);
            TableInfo tableInfo = TableInfo.newBuilder().setName(tname).setSegCnt(8).build();
            Assert.assertTrue(nsc.createTable(tableInfo));
            List<TableInfo> tables = nsc.showTable(tname);
            Assert.assertTrue(tables.size() == 1);
            Assert.assertEquals(tables.get(0).getStorageMode(), NS.StorageMode.kMemory);
            Assert.assertTrue(nsc.dropTable(tname));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testAllFlow() {
        Random rand = new Random(System.currentTimeMillis());
        String tname = rand.nextInt() + "tname";
        PartitionMeta pm = PartitionMeta.newBuilder().setEndpoint(nodes[0]).setIsLeader(true).build();
        TablePartition tp = TablePartition.newBuilder().addPartitionMeta(pm).setPid(0).build();
        TableInfo tableInfo = TableInfo.newBuilder().setName(tname).setSegCnt(8).addTablePartition(tp).build();
        try {
            Assert.assertTrue(true);
            Assert.assertTrue(nsc.createTable(tableInfo));
            List<TableInfo> tables = nsc.showTable(tname);
            Map<String, String> nscMap = nsc.showNs();
            Assert.assertTrue(nscMap.size() == 3);
            TableInfo e = tables.get(0);
            Assert.assertTrue(e.getTablePartitionList().size() == 1);
            Assert.assertTrue(e.getTablePartition(0).getRecordCnt() == 0);
            Assert.assertTrue(e.getTablePartition(0).getRecordCnt() == 0);
            Assert.assertTrue(tables.size() == 1);
            Assert.assertTrue(tables.get(0).getName().equals(tname));
            Assert.assertTrue(nsc.dropTable(tname));
            tables = nsc.showTable(tname);
            Assert.assertTrue(tables.size() == 0);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testCreateTableTTL() {
        try {
            int max_ttl = 60 * 24 * 365 * 30;
            nsc.dropTable("t1_ttl");
            TableInfo tableInfo1 = TableInfo.newBuilder().setName("t1_ttl").setSegCnt(8).setTtl(max_ttl + 1).build();
            Assert.assertFalse(nsc.createTable(tableInfo1));
            nsc.dropTable("t2_ttl");
            TableInfo tableInfo2 = TableInfo.newBuilder().setName("t2_ttl").setSegCnt(8).setTtl(max_ttl).build();
            Assert.assertTrue(nsc.createTable(tableInfo2));
            Assert.assertTrue(nsc.dropTable("t2_ttl"));
            nsc.dropTable("t3_ttl");
            TableInfo tableInfo3 = TableInfo.newBuilder().setName("t3_ttl").setSegCnt(8).setTtlType("kLatestTime").setTtl(1001).build();
            Assert.assertFalse(nsc.createTable(tableInfo3));
            nsc.dropTable("t4_ttl");
            TableInfo tableInfo4 = TableInfo.newBuilder().setName("t4_ttl").setSegCnt(8).setTtlType("kLatestTime").setTtl(1000).build();
            Assert.assertTrue(nsc.createTable(tableInfo4));
            Assert.assertTrue(nsc.dropTable("t4_ttl"));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testCreateTableHDD() {
        NameServerClientImpl nsc = new NameServerClientImpl(config);
        try {
            nsc.init();
            String name = "t1";
            TableInfo tableInfo1 = TableInfo.newBuilder().setName(name).setSegCnt(8).setTtl(0).setReplicaNum(1)
                    .setStorageMode(NS.StorageMode.kHDD).build();
            Assert.assertTrue(nsc.createTable(tableInfo1));
            List<TableInfo> tables = nsc.showTable(name);
            Assert.assertTrue(tables.size() == 1);
            Assert.assertEquals(tables.get(0).getStorageMode(), NS.StorageMode.kHDD);
            Assert.assertTrue(nsc.dropTable(name));
        } catch(Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testCreateTableSSD() {
        NameServerClientImpl nsc = new NameServerClientImpl(config);
        try {
            nsc.init();
            String name = "t1";
            TableInfo tableInfo1 = TableInfo.newBuilder().setName(name).setSegCnt(8).setTtl(0).setReplicaNum(1)
                    .setStorageMode(NS.StorageMode.kSSD).build();
            Assert.assertTrue(nsc.createTable(tableInfo1));
            List<TableInfo> tables = nsc.showTable(name);
            Assert.assertTrue(tables.size() == 1);
            Assert.assertEquals(tables.get(0).getStorageMode(), NS.StorageMode.kSSD);
            Assert.assertTrue(nsc.dropTable(name));
        } catch(Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    @Test
    public void testTableHandler() {
        NS.TableInfo.Builder builder = NS.TableInfo.newBuilder()
                .setName("test")  // 设置表名
                .setTtl(144000);      // 设置ttl
        NS.ColumnDesc col0 = NS.ColumnDesc.newBuilder().setName("col_0").setAddTsIdx(true).setType("string").build();
        NS.ColumnDesc col1 = NS.ColumnDesc.newBuilder().setName("col_1").setAddTsIdx(true).setType("int64").build();
        NS.ColumnDesc col2 = NS.ColumnDesc.newBuilder().setName("col_2").setAddTsIdx(false).setType("double").build();
        NS.ColumnDesc col3 = NS.ColumnDesc.newBuilder().setName("col_3").setAddTsIdx(false).setType("float").build();
        builder.addColumnDesc(col0).addColumnDesc(col1).addColumnDesc(col2).addColumnDesc(col3);
        NS.TableInfo tableInfo = builder.build();
        TableHandler tableHandler = new TableHandler(tableInfo);
        List<ColumnDesc> schema = tableHandler.getSchema();

        Assert.assertTrue(schema.size() == 4, "schema size mistook");
        Assert.assertTrue((schema.get(0).getName().equals("col_0"))
                && (schema.get(0).isAddTsIndex() == true)
                && (schema.get(0).getType().toString().equals("kString")), "col_0 mistook");
        Assert.assertTrue(schema.get(1).getName().equals("col_1")
                && schema.get(1).isAddTsIndex() == true
                && schema.get(1).getType().toString().equals("kInt64"), "col_1 mistook");
        Assert.assertTrue(schema.get(2).getName().equals("col_2")
                && schema.get(2).isAddTsIndex() == false
                && schema.get(2).getType().toString().equals("kDouble"), "col_2 mistook");
        Assert.assertTrue(schema.get(3).getName().equals("col_3")
                && schema.get(3).isAddTsIndex() == false
                && schema.get(3).getType().toString().equals("kFloat"), "col_3 mistook");

        Map<Integer, List<Integer>> indexes = tableHandler.getIndexes();
        Assert.assertTrue(indexes.size() == 2, "indexes size mistook");
        Assert.assertTrue(indexes.get(0).size() == 1);
        Assert.assertTrue(indexes.get(1).size() == 1);
    }
}
