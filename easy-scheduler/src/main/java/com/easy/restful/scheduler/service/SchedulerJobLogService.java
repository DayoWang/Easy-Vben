package com.easy.restful.scheduler.service;

import com.easy.restful.common.core.common.pagination.Page;
import com.easy.restful.scheduler.model.SchedulerJobLog;

/**
 * 定时任务执行日志
 *
 * @author TengChong
 * @date 2019-05-11
 */
public interface SchedulerJobLogService {
    /**
     * 列表
     *
     * @param object 查询条件
     * @param page   分页
     * @return Page<SchedulerJobLog>
     */
    Page<SchedulerJobLog> select(SchedulerJobLog object, Page<SchedulerJobLog> page);


    /**
     * 保存
     *
     * @param object 表单内容
     * @return 保存后信息
     */
    SchedulerJobLog saveData(SchedulerJobLog object);
}
