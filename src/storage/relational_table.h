//
// Created by wangbao on 02/24/20. 
//

#pragma once

#include <vector>
#include <map>
#include <atomic>
#include "proto/common.pb.h"
#include "proto/tablet.pb.h"
#include <gflags/gflags.h>
#include <rocksdb/db.h>
#include <rocksdb/slice.h>
#include <rocksdb/options.h>
#include <rocksdb/status.h>
#include <rocksdb/table.h>
#include <rocksdb/slice_transform.h>
#include <rocksdb/filter_policy.h>
#include <rocksdb/compaction_filter.h>
#include <rocksdb/utilities/checkpoint.h>
#include "base/slice.h"
#include "base/endianconv.h"
#include "storage/iterator.h"
#include "storage/table.h"
#include <boost/lexical_cast.hpp>
#include "timer.h"
#include "base/codec.h"
#include <mutex>
#include "base/id_generator.h"

typedef google::protobuf::RepeatedPtrField<::rtidb::api::Dimension> Dimensions;
using Schema = ::google::protobuf::RepeatedPtrField<::rtidb::common::ColumnDesc>;

namespace rtidb {
namespace storage {

class RelationalTable;

class RelationalTableTraverseIterator {
 public:
    RelationalTableTraverseIterator(RelationalTable* table, rocksdb::Iterator* it,
                                    uint64_t id);
    ~RelationalTableTraverseIterator();
    bool Valid();
    void Next();
    void SeekToFirst();
    void Seek(const std::string& pk);
    uint64_t GetCount();
    rtidb::base::Slice GetValue();
    uint64_t GetSeq();
    rocksdb::Slice GetKey();
    void SetFinish(bool finish);

 private:
    RelationalTable* table_;
    rocksdb::Iterator* it_;
    uint64_t traverse_cnt_;
    bool finish_;
    uint64_t id_;
};

struct SnapshotInfo {
    const rocksdb::Snapshot* snapshot;
    uint64_t atime;
};

class RelationalTable {

public:
    RelationalTable(const std::string& name,
                uint32_t id,
                uint32_t pid,
                const std::map<std::string, uint32_t>& mapping,
                ::rtidb::common::StorageMode storage_mode,
                const std::string& db_root_path);

    RelationalTable(const ::rtidb::api::TableMeta& table_meta,
              const std::string& db_root_path);
    RelationalTable(const RelationalTable&) = delete;
    RelationalTable& operator=(const RelationalTable&) = delete;

    ~RelationalTable();

    bool InitColumnFamilyDescriptor();

    int InitColumnDesc();
    
    bool InitFromMeta();

    bool Init();

    static void initOptionTemplate();

    bool Put(const std::string& value); 

    bool PutDB(const std::string& pk,
             const char* data,
             uint32_t size);

    bool Put(const std::string& value,
             const Dimensions& dimensions);

    bool Get(uint32_t idx, const std::string& pk, rtidb::base::Slice& slice);

    bool Delete(const std::string& pk, uint32_t idx);

    rtidb::storage::RelationalTableTraverseIterator* NewTraverse(uint32_t idx, uint64_t snapshot_id);

    void ReleaseSnpashot(uint64_t seq, bool finish);

    void TTLSnapshot();

    bool Update(const ::rtidb::api::Columns& cd_columns, 
            const ::rtidb::api::Columns& col_columns);

    void UpdateInternel(const ::rtidb::api::Columns& cd_columns, 
            std::map<std::string, int>& cd_idx_map, 
            Schema& condition_schema);
        
    bool UpdateDB(const std::map<std::string, int>& cd_idx_map, const std::map<std::string, int>& col_idx_map, const Schema& condition_schema, const Schema& value_schema, 
            const std::string& cd_value, const std::string& col_value); 

    inline ::rtidb::common::StorageMode GetStorageMode() const {
        return storage_mode_;
    }

    inline uint32_t GetTableStat() {
        return table_status_.load(std::memory_order_relaxed);
    }

    inline uint32_t GetIdxCnt() const {
        return idx_cnt_;
    }

    inline void SetTableStat(uint32_t table_status) {
        table_status_.store(table_status, std::memory_order_relaxed);
    }

    uint64_t GetRecordCnt() const {
        uint64_t count = 0;
        if (cf_hs_.size() == 1)
            db_->GetIntProperty(cf_hs_[0], "rocksdb.estimate-num-keys", &count);
        else {
            db_->GetIntProperty(cf_hs_[1], "rocksdb.estimate-num-keys", &count);
        }
        return count;
    }

    uint64_t GetOffset() {
        return offset_.load(std::memory_order_relaxed);
    }

    void SetOffset(uint64_t offset) {
        offset_.store(offset, std::memory_order_relaxed);
    }

    inline std::map<std::string, uint32_t>& GetMapping() {
        return mapping_;
    }

    inline ::rtidb::api::TableMeta& GetTableMeta() {
        return table_meta_;
    }
    
private:
    std::mutex mu_;
    ::rtidb::common::StorageMode storage_mode_;
    std::string name_;
    uint32_t id_;
    uint32_t pid_;
    uint32_t idx_cnt_;
    std::atomic<uint64_t> diskused_;
    bool is_leader_;
    std::atomic<uint32_t> table_status_;
    std::string schema_;
    std::map<std::string, uint32_t> mapping_;
    ::rtidb::api::CompressType compress_type_;
    ::rtidb::api::TableMeta table_meta_;
    int64_t last_make_snapshot_time_;

    rocksdb::DB* db_;
    rocksdb::WriteOptions write_opts_;
    std::vector<rocksdb::ColumnFamilyDescriptor> cf_ds_;
    std::vector<rocksdb::ColumnFamilyHandle*> cf_hs_;
    rocksdb::Options options_;
    std::atomic<uint64_t> offset_;
    std::string db_root_path_;

    ::rtidb::base::IdGenerator id_generator_;

    std::map<uint64_t, std::shared_ptr<SnapshotInfo>> snapshots_;
    uint64_t snapshot_index_; // 0 is invalid snapshot_index
};

}
}