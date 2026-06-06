/**
 * Log analysis and parsing subsystem.
 * <p>
 * Provides multi-format log parsing through the {@link com.rca.agent.analyzer.log.LogParser}
 * strategy interface. Supported formats:
 * <ul>
 *   <li>Structured JSON logs (one JSON object per line)</li>
 *   <li>Unstructured plaintext logs (regex-based extraction)</li>
 * </ul>
 */
package com.rca.agent.analyzer.log;
