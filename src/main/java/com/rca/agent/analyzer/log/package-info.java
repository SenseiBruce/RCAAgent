/**
 * Log analysis and parsing subsystem.
 *
 * <p>Provides multi-format log parsing through the {@link com.rca.agent.analyzer.log.LogParser}
 * strategy interface. Supported formats:
 *
 * <ul>
 *   <li>Structured JSON logs (one JSON object per line)
 *   <li>Unstructured plaintext logs (regex-based extraction)
 * </ul>
 */
package com.rca.agent.analyzer.log;
