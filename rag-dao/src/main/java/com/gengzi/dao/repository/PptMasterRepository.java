package com.gengzi.dao.repository;

import com.gengzi.dao.PptMaster;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PptMasterRepository extends JpaRepository<PptMaster, Long>, JpaSpecificationExecutor<PptMaster> {
    List<PptMaster> findPptMasterByName(@Size(max = 255) @NotNull String name);
}