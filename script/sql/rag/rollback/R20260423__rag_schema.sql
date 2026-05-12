-- ============================================================
-- Script type: rollback
-- Version: R20260423
-- Module: rag
-- Description: Drop RAG schema created by B20260423__rag_schema.sql.
-- ============================================================

drop table if exists t_rag_trace_node;
drop table if exists t_rag_trace_run;
drop table if exists t_query_term_mapping;
drop table if exists t_intent_node;
drop table if exists t_sample_question;
drop table if exists t_message_feedback;
drop table if exists t_message;
drop table if exists t_conversation_summary;
drop table if exists t_conversation;
drop table if exists t_ingestion_task_node;
drop table if exists t_ingestion_task;
drop table if exists t_knowledge_document_schedule_exec;
drop table if exists t_knowledge_document_schedule;
drop table if exists t_knowledge_document_chunk_log;
drop table if exists t_knowledge_vector;
drop table if exists t_knowledge_chunk;
drop table if exists t_knowledge_document;
drop table if exists t_knowledge_base;
drop table if exists t_ingestion_pipeline_node;
drop table if exists t_ingestion_pipeline;


