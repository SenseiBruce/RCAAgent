/**
 * Source code context extraction subsystem.
 * <p>
 * Extracts file and line number references from error logs, locates the
 * corresponding source files in the repository, and reads the surrounding
 * code to provide context for LLM-based root cause analysis.
 */
package com.rca.agent.analyzer.code;
