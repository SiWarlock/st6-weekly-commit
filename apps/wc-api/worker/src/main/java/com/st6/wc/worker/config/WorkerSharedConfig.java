package com.st6.wc.worker.config;

import com.st6.wc.config.ClockConfig;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires cross-module {@code :shared} beans into the worker context. {@code WcSyncWorkerApplication}
 * lives in {@code com.st6.wc.worker}, so the {@code :shared} packages (under {@code com.st6.wc.*})
 * are outside its default component scan and must be wired explicitly:
 *
 * <ul>
 *   <li>{@link Import @Import}({@code ClockConfig}) — the injectable {@code Clock} (§17 / §10
 *       time-based sync transitions).
 *   <li>{@link EntityScan @EntityScan}({@code com.st6.wc}) — the JPA entities, so {@code
 *       ddl-auto=validate} validates the worker's full view against the migration-owned schema
 *       (Wave-2 s8 — the worker reloads {@code OutlookCalendarSyncRecord}).
 *   <li>{@link EnableJpaRepositories @EnableJpaRepositories}({@code com.st6.wc.sync.repo} + {@code
 *       com.st6.wc.employee.repo}) — only the repos the worker actually uses (the sync repo for the
 *       SQS consumer, the employee repo for the s9 owner-email → Graph lookup), not every domain
 *       repo.
 * </ul>
 */
@Configuration
@Import(ClockConfig.class)
@EntityScan("com.st6.wc")
@EnableJpaRepositories({"com.st6.wc.sync.repo", "com.st6.wc.employee.repo"})
public class WorkerSharedConfig {}
