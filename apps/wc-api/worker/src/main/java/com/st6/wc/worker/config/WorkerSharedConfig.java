package com.st6.wc.worker.config;

import com.st6.wc.config.ClockConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires cross-module {@code :shared} beans into the worker context. {@code WcSyncWorkerApplication}
 * lives in {@code com.st6.wc.worker}, so the sibling {@code com.st6.wc.config.ClockConfig} is
 * outside its default component scan — import it explicitly so the injectable {@code Clock} (§17 /
 * §10 time-based sync transitions) is present in the worker (flag 6).
 */
@Configuration
@Import(ClockConfig.class)
public class WorkerSharedConfig {}
