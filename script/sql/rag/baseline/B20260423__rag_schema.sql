-- ============================================================
-- Script type: baseline
-- Version: B20260423
-- Module: rag
-- Description: RAG schema in the main application database.
-- Notes:
--   1. This script does not create a new database.
--   2. All RAG tables use the rag_ prefix.
--   3. user_id points to sys_user.user_id by logical reference.
--   4. Embedding vectors are stored in Milvus; MySQL stores metadata only.
-- ============================================================

create table if not exists rag_user_ext (
    id             bigint not null auto_increment comment 'Primary key',
    user_id        bigint not null comment 'Logical reference to sys_user.user_id',
    avatar_url     varchar(512) default null comment 'RAG avatar URL',
    rag_role       varchar(32) not null default 'USER' comment 'RAG role: USER/ADMIN',
    quota_limit    int default null comment 'Optional quota limit',
    quota_used     int not null default 0 comment 'Used quota counter',
    preferences    json default null comment 'RAG user preferences',
    status         varchar(16) not null default 'ACTIVE' comment 'ACTIVE/DISABLED',
    deleted        tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at     datetime not null default current_timestamp comment 'Create time',
    updated_at     datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_user_ext_user_deleted (user_id, deleted),
    key idx_rag_user_ext_status_deleted (status, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG user extension';

create table if not exists rag_ingestion_pipeline (
    id             bigint not null auto_increment comment 'Primary key',
    name           varchar(100) not null comment 'Pipeline name',
    description    text default null comment 'Pipeline description',
    created_by     bigint default null comment 'Creator sys_user.user_id',
    updated_by     bigint default null comment 'Updater sys_user.user_id',
    deleted        tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at     datetime not null default current_timestamp comment 'Create time',
    updated_at     datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_ingestion_pipeline_name_deleted (name, deleted),
    key idx_rag_ingestion_pipeline_created_by (created_by)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG ingestion pipeline';

create table if not exists rag_ingestion_pipeline_node (
    id             bigint not null auto_increment comment 'Primary key',
    pipeline_id    bigint not null comment 'Pipeline id',
    node_id        varchar(64) not null comment 'Node id inside the pipeline',
    node_type      varchar(30) not null comment 'Node type',
    next_node_id   varchar(64) default null comment 'Next node id',
    settings_json  json default null comment 'Node settings',
    condition_json json default null comment 'Node condition',
    created_by     bigint default null comment 'Creator sys_user.user_id',
    updated_by     bigint default null comment 'Updater sys_user.user_id',
    deleted        tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at     datetime not null default current_timestamp comment 'Create time',
    updated_at     datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_ingestion_pipeline_node (pipeline_id, node_id, deleted),
    key idx_rag_ingestion_pipeline_node_pipeline (pipeline_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG ingestion pipeline node';

create table if not exists rag_knowledge_base (
    id              bigint not null auto_increment comment 'Primary key',
    name            varchar(128) not null comment 'Knowledge base name',
    description     varchar(512) default null comment 'Knowledge base description',
    embedding_model varchar(128) not null comment 'Embedding model',
    collection_name varchar(128) not null comment 'Milvus collection name',
    visibility      varchar(16) not null default 'PRIVATE' comment 'PRIVATE/TEAM/PUBLIC',
    status          varchar(16) not null default 'ACTIVE' comment 'ACTIVE/DISABLED',
    owner_user_id   bigint default null comment 'Owner sys_user.user_id',
    created_by      bigint default null comment 'Creator sys_user.user_id',
    updated_by      bigint default null comment 'Updater sys_user.user_id',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_kb_collection_deleted (collection_name, deleted),
    key idx_rag_kb_name_deleted (name, deleted),
    key idx_rag_kb_owner_deleted (owner_user_id, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG knowledge base';

create table if not exists rag_knowledge_document (
    id               bigint not null auto_increment comment 'Primary key',
    kb_id            bigint not null comment 'Knowledge base id',
    doc_name         varchar(256) not null comment 'Document name',
    enabled          tinyint not null default 1 comment 'Enabled flag: 0 disabled, 1 enabled',
    chunk_count      int not null default 0 comment 'Chunk count',
    file_url         varchar(1024) default null comment 'Stored file URL',
    file_type        varchar(32) default null comment 'File type',
    file_size        bigint default null comment 'File size in bytes',
    process_mode     varchar(32) not null default 'chunk' comment 'chunk/pipeline',
    status           varchar(32) not null default 'pending' comment 'pending/running/success/failed',
    source_type      varchar(32) default null comment 'file/url/text/api',
    source_location  varchar(1024) default null comment 'Original source location',
    content_hash     varchar(128) default null comment 'Source content hash',
    schedule_enabled tinyint not null default 0 comment 'Schedule enabled flag',
    schedule_cron    varchar(128) default null comment 'Schedule cron expression',
    chunk_strategy   varchar(32) default null comment 'Chunk strategy',
    chunk_config     json default null comment 'Chunk config',
    pipeline_id      bigint default null comment 'Ingestion pipeline id',
    created_by       bigint default null comment 'Creator sys_user.user_id',
    updated_by       bigint default null comment 'Updater sys_user.user_id',
    deleted          tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at       datetime not null default current_timestamp comment 'Create time',
    updated_at       datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_doc_kb_deleted (kb_id, deleted),
    key idx_rag_doc_status_deleted (status, deleted),
    key idx_rag_doc_created_by (created_by),
    key idx_rag_doc_content_hash (content_hash)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG knowledge document';

create table if not exists rag_knowledge_chunk (
    id           bigint not null auto_increment comment 'Primary key',
    kb_id        bigint not null comment 'Knowledge base id',
    doc_id       bigint not null comment 'Document id',
    chunk_index  int not null comment 'Chunk index from 0',
    content      longtext not null comment 'Chunk content',
    content_hash varchar(128) default null comment 'Chunk content hash',
    char_count   int default null comment 'Character count',
    token_count  int default null comment 'Token count',
    enabled      tinyint not null default 1 comment 'Enabled flag: 0 disabled, 1 enabled',
    metadata     json default null comment 'Chunk metadata',
    created_by   bigint default null comment 'Creator sys_user.user_id',
    updated_by   bigint default null comment 'Updater sys_user.user_id',
    deleted      tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at   datetime not null default current_timestamp comment 'Create time',
    updated_at   datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_chunk_doc_index_deleted (doc_id, chunk_index, deleted),
    key idx_rag_chunk_kb_deleted (kb_id, deleted),
    key idx_rag_chunk_doc_deleted (doc_id, deleted),
    key idx_rag_chunk_hash (content_hash)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG knowledge chunk';

create table if not exists rag_knowledge_vector_ref (
    id              bigint not null auto_increment comment 'Primary key',
    kb_id           bigint not null comment 'Knowledge base id',
    doc_id          bigint not null comment 'Document id',
    chunk_id        bigint not null comment 'Chunk id',
    collection_name varchar(128) not null comment 'Milvus collection name',
    vector_id       varchar(128) not null comment 'Vector id in Milvus',
    embedding_model varchar(128) not null comment 'Embedding model',
    embedding_dim   int default null comment 'Embedding dimension',
    metadata        json default null comment 'Vector metadata',
    created_by      bigint default null comment 'Creator sys_user.user_id',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_vector_ref_chunk_deleted (chunk_id, deleted),
    unique key uk_rag_vector_ref_vector_deleted (collection_name, vector_id, deleted),
    key idx_rag_vector_ref_kb_deleted (kb_id, deleted),
    key idx_rag_vector_ref_doc_deleted (doc_id, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG vector metadata reference';

create table if not exists rag_knowledge_document_chunk_log (
    id                bigint not null auto_increment comment 'Primary key',
    doc_id            bigint not null comment 'Document id',
    status            varchar(20) not null comment 'Execution status',
    process_mode      varchar(20) default null comment 'Process mode',
    chunk_strategy    varchar(50) default null comment 'Chunk strategy',
    pipeline_id       bigint default null comment 'Pipeline id',
    extract_duration  bigint default null comment 'Extract duration in ms',
    chunk_duration    bigint default null comment 'Chunk duration in ms',
    embed_duration    bigint default null comment 'Embedding duration in ms',
    persist_duration  bigint default null comment 'Persistence duration in ms',
    total_duration    bigint default null comment 'Total duration in ms',
    chunk_count       int default null comment 'Generated chunk count',
    error_message     text default null comment 'Error message',
    started_at        datetime default null comment 'Start time',
    ended_at          datetime default null comment 'End time',
    created_at        datetime not null default current_timestamp comment 'Create time',
    updated_at        datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_doc_chunk_log_doc (doc_id),
    key idx_rag_doc_chunk_log_status (status),
    key idx_rag_doc_chunk_log_started (started_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG document chunk log';

create table if not exists rag_knowledge_document_schedule (
    id                bigint not null auto_increment comment 'Primary key',
    doc_id            bigint not null comment 'Document id',
    kb_id             bigint not null comment 'Knowledge base id',
    cron_expr         varchar(128) default null comment 'Cron expression',
    enabled           tinyint not null default 0 comment 'Enabled flag',
    next_run_time     datetime default null comment 'Next run time',
    last_run_time     datetime default null comment 'Last run time',
    last_success_time datetime default null comment 'Last success time',
    last_status       varchar(32) default null comment 'Last status',
    last_error        varchar(512) default null comment 'Last error',
    last_etag         varchar(256) default null comment 'Last ETag',
    last_modified     varchar(256) default null comment 'Last-Modified',
    last_content_hash varchar(128) default null comment 'Last content hash',
    lock_owner        varchar(128) default null comment 'Lock owner',
    lock_until        datetime default null comment 'Lock expiration time',
    created_at        datetime not null default current_timestamp comment 'Create time',
    updated_at        datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_doc_schedule_doc (doc_id),
    key idx_rag_doc_schedule_kb (kb_id),
    key idx_rag_doc_schedule_next_run (next_run_time),
    key idx_rag_doc_schedule_lock_until (lock_until)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG document refresh schedule';

create table if not exists rag_knowledge_document_schedule_exec (
    id            bigint not null auto_increment comment 'Primary key',
    schedule_id   bigint not null comment 'Schedule id',
    doc_id        bigint not null comment 'Document id',
    kb_id         bigint not null comment 'Knowledge base id',
    status        varchar(32) not null comment 'Execution status',
    message       varchar(512) default null comment 'Execution message',
    started_at    datetime default null comment 'Start time',
    ended_at      datetime default null comment 'End time',
    file_name     varchar(512) default null comment 'File name',
    file_size     bigint default null comment 'File size',
    content_hash  varchar(128) default null comment 'Content hash',
    etag          varchar(256) default null comment 'ETag',
    last_modified varchar(256) default null comment 'Last-Modified',
    created_at    datetime not null default current_timestamp comment 'Create time',
    updated_at    datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_doc_schedule_exec_schedule_time (schedule_id, started_at),
    key idx_rag_doc_schedule_exec_doc (doc_id),
    key idx_rag_doc_schedule_exec_kb (kb_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG document schedule execution';

create table if not exists rag_ingestion_task (
    id               bigint not null auto_increment comment 'Primary key',
    pipeline_id      bigint default null comment 'Pipeline id',
    kb_id            bigint default null comment 'Knowledge base id',
    doc_id           bigint default null comment 'Document id',
    source_type      varchar(20) not null comment 'Source type',
    source_location  text default null comment 'Source URL or location',
    source_file_name varchar(255) default null comment 'Original file name',
    status           varchar(20) not null comment 'Task status',
    chunk_count      int not null default 0 comment 'Chunk count',
    error_message    text default null comment 'Error message',
    logs_json        json default null comment 'Task logs',
    metadata_json    json default null comment 'Task metadata',
    started_at       datetime default null comment 'Start time',
    completed_at     datetime default null comment 'Complete time',
    created_by       bigint default null comment 'Creator sys_user.user_id',
    updated_by       bigint default null comment 'Updater sys_user.user_id',
    deleted          tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at       datetime not null default current_timestamp comment 'Create time',
    updated_at       datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_ingestion_task_pipeline (pipeline_id),
    key idx_rag_ingestion_task_kb_doc (kb_id, doc_id),
    key idx_rag_ingestion_task_status (status),
    key idx_rag_ingestion_task_created_by (created_by)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG ingestion task';

create table if not exists rag_ingestion_task_node (
    id            bigint not null auto_increment comment 'Primary key',
    task_id       bigint not null comment 'Task id',
    pipeline_id   bigint default null comment 'Pipeline id',
    node_id       varchar(64) not null comment 'Node id',
    node_type     varchar(30) not null comment 'Node type',
    node_order    int not null default 0 comment 'Node order',
    status        varchar(20) not null comment 'Node status',
    duration_ms   bigint not null default 0 comment 'Duration in ms',
    message       text default null comment 'Node message',
    error_message text default null comment 'Error message',
    output_json   longtext default null comment 'Full node output JSON',
    deleted       tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at    datetime not null default current_timestamp comment 'Create time',
    updated_at    datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_ingestion_task_node_task (task_id),
    key idx_rag_ingestion_task_node_pipeline (pipeline_id),
    key idx_rag_ingestion_task_node_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG ingestion task node';

create table if not exists rag_conversation (
    id              bigint not null auto_increment comment 'Primary key',
    conversation_id varchar(64) not null comment 'Business conversation id',
    user_id         bigint not null comment 'Logical reference to sys_user.user_id',
    title           varchar(128) not null comment 'Conversation title',
    last_time       datetime default null comment 'Last message time',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_conversation_user (conversation_id, user_id),
    key idx_rag_conversation_user_time (user_id, last_time),
    key idx_rag_conversation_deleted (deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG conversation';

create table if not exists rag_conversation_summary (
    id              bigint not null auto_increment comment 'Primary key',
    conversation_id varchar(64) not null comment 'Business conversation id',
    user_id         bigint not null comment 'Logical reference to sys_user.user_id',
    last_message_id bigint not null comment 'Last summarized message id',
    content         text not null comment 'Conversation summary',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_conversation_summary_conv_user (conversation_id, user_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG conversation summary';

create table if not exists rag_message (
    id                bigint not null auto_increment comment 'Primary key',
    conversation_id   varchar(64) not null comment 'Business conversation id',
    user_id           bigint not null comment 'Logical reference to sys_user.user_id',
    role              varchar(32) not null comment 'system/user/assistant/tool',
    content           longtext not null comment 'Message content',
    thinking_content  text default null comment 'Reasoning content',
    thinking_duration int default null comment 'Reasoning duration in seconds',
    metadata          json default null comment 'Message metadata',
    deleted           tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at        datetime not null default current_timestamp comment 'Create time',
    updated_at        datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_message_conv_user_time (conversation_id, user_id, created_at),
    key idx_rag_message_user_time (user_id, created_at)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG message';

create table if not exists rag_message_feedback (
    id              bigint not null auto_increment comment 'Primary key',
    message_id      bigint not null comment 'Message id',
    conversation_id varchar(64) not null comment 'Business conversation id',
    user_id         bigint not null comment 'Logical reference to sys_user.user_id',
    vote            tinyint not null comment '1 upvote, -1 downvote',
    reason          varchar(255) default null comment 'Feedback reason',
    comment_text    varchar(1024) default null comment 'Feedback comment',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_message_feedback_msg_user (message_id, user_id),
    key idx_rag_message_feedback_conversation (conversation_id),
    key idx_rag_message_feedback_user (user_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG message feedback';

create table if not exists rag_sample_question (
    id          bigint not null auto_increment comment 'Primary key',
    kb_id       bigint default null comment 'Knowledge base id',
    title       varchar(64) default null comment 'Display title',
    description varchar(255) default null comment 'Description',
    question    varchar(1024) not null comment 'Sample question',
    sort_order  int not null default 0 comment 'Sort order',
    enabled     tinyint not null default 1 comment 'Enabled flag',
    deleted     tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at  datetime not null default current_timestamp comment 'Create time',
    updated_at  datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_sample_question_kb_deleted (kb_id, deleted),
    key idx_rag_sample_question_enabled (enabled, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG sample question';

create table if not exists rag_intent_node (
    id                    bigint not null auto_increment comment 'Primary key',
    kb_id                 bigint default null comment 'Knowledge base id',
    intent_code           varchar(64) not null comment 'Unique intent code',
    name                  varchar(64) not null comment 'Display name',
    level                 tinyint not null comment '0 domain, 1 category, 2 topic',
    parent_code           varchar(64) default null comment 'Parent intent code',
    description           varchar(512) default null comment 'Semantic description',
    examples              text default null comment 'Example questions',
    collection_name       varchar(128) default null comment 'Related Milvus collection',
    top_k                 int default null comment 'Retrieval topK',
    mcp_tool_id           varchar(128) default null comment 'MCP tool id',
    kind                  tinyint not null default 0 comment '0 RAG, 1 system interaction',
    prompt_snippet        text default null comment 'Prompt snippet',
    prompt_template       text default null comment 'Prompt template',
    param_prompt_template text default null comment 'Parameter extraction prompt template',
    sort_order            int not null default 0 comment 'Sort order',
    enabled               tinyint not null default 1 comment 'Enabled flag',
    created_by            bigint default null comment 'Creator sys_user.user_id',
    updated_by            bigint default null comment 'Updater sys_user.user_id',
    deleted               tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at            datetime not null default current_timestamp comment 'Create time',
    updated_at            datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_intent_code_deleted (intent_code, deleted),
    key idx_rag_intent_kb_deleted (kb_id, deleted),
    key idx_rag_intent_parent (parent_code),
    key idx_rag_intent_enabled (enabled, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG intent tree node';

create table if not exists rag_query_term_mapping (
    id          bigint not null auto_increment comment 'Primary key',
    domain      varchar(64) default null comment 'Business domain',
    source_term varchar(128) not null comment 'Original user term',
    target_term varchar(128) not null comment 'Normalized term',
    match_type  tinyint not null default 1 comment '1 exact, 2 prefix, 3 regex, 4 whole word',
    priority    int not null default 100 comment 'Lower value means higher priority',
    enabled     tinyint not null default 1 comment 'Enabled flag',
    remark      varchar(255) default null comment 'Remark',
    created_by  bigint default null comment 'Creator sys_user.user_id',
    updated_by  bigint default null comment 'Updater sys_user.user_id',
    deleted     tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at  datetime not null default current_timestamp comment 'Create time',
    updated_at  datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    key idx_rag_term_domain (domain),
    key idx_rag_term_source (source_term),
    key idx_rag_term_enabled (enabled, deleted)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG query term mapping';

create table if not exists rag_trace_run (
    id              bigint not null auto_increment comment 'Primary key',
    trace_id        varchar(64) not null comment 'Global trace id',
    trace_name      varchar(128) default null comment 'Trace name',
    entry_method    varchar(256) default null comment 'Entry method',
    conversation_id varchar(64) default null comment 'Business conversation id',
    task_id         varchar(64) default null comment 'Task id',
    user_id         bigint default null comment 'Logical reference to sys_user.user_id',
    status          varchar(16) not null default 'RUNNING' comment 'RUNNING/SUCCESS/ERROR',
    error_message   varchar(1000) default null comment 'Error message',
    started_at      datetime(3) default null comment 'Start time',
    ended_at        datetime(3) default null comment 'End time',
    duration_ms     bigint default null comment 'Duration in ms',
    extra_data      json default null comment 'Extra data',
    deleted         tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at      datetime not null default current_timestamp comment 'Create time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_trace_run_trace (trace_id),
    key idx_rag_trace_run_task (task_id),
    key idx_rag_trace_run_user (user_id),
    key idx_rag_trace_run_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG trace run';

create table if not exists rag_trace_node (
    id             bigint not null auto_increment comment 'Primary key',
    trace_id       varchar(64) not null comment 'Global trace id',
    node_id        varchar(64) not null comment 'Trace node id',
    parent_node_id varchar(64) default null comment 'Parent node id',
    depth          int not null default 0 comment 'Node depth',
    node_type      varchar(64) default null comment 'Node type',
    node_name      varchar(128) default null comment 'Node name',
    class_name     varchar(256) default null comment 'Class name',
    method_name    varchar(128) default null comment 'Method name',
    status         varchar(16) not null default 'RUNNING' comment 'RUNNING/SUCCESS/ERROR',
    error_message  varchar(1000) default null comment 'Error message',
    started_at     datetime(3) default null comment 'Start time',
    ended_at       datetime(3) default null comment 'End time',
    duration_ms    bigint default null comment 'Duration in ms',
    extra_data     json default null comment 'Extra data',
    deleted        tinyint not null default 0 comment 'Logical delete flag: 0 normal, 1 deleted',
    created_at     datetime not null default current_timestamp comment 'Create time',
    updated_at     datetime not null default current_timestamp on update current_timestamp comment 'Update time',
    primary key (id),
    unique key uk_rag_trace_node_run_node (trace_id, node_id),
    key idx_rag_trace_node_parent (trace_id, parent_node_id),
    key idx_rag_trace_node_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='RAG trace node';
