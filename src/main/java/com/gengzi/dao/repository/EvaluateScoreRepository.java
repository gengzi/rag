package com.gengzi.dao.repository;

import com.gengzi.dao.EvaluateScore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluateScoreRepository extends JpaRepository<EvaluateScore, Long>, JpaSpecificationExecutor<EvaluateScore> {
    List<EvaluateScore> findByBatchNum(@Size(max = 32) @NotNull String batchNum);
}