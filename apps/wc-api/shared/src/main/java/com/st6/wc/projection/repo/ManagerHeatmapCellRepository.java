package com.st6.wc.projection.repo;

import com.st6.wc.projection.ManagerHeatmapCell;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link ManagerHeatmapCell} (task 1.5). Finders land in 1.6. */
public interface ManagerHeatmapCellRepository extends JpaRepository<ManagerHeatmapCell, UUID> {}
