package com.gengzi.dao.repository;

import com.gengzi.dao.EvaluateScore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Repository
public interface EvaluateScoreRepository extends JpaRepository<EvaluateScore, Long>, JpaSpecificationExecutor<EvaluateScore> {
    List<EvaluateScore> findByBatchNum(@Size(max = 32) @NotNull String batchNum);
}