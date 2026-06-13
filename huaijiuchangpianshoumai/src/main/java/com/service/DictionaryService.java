package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.DictionaryEntity;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * 字典 服务类
 */
public interface DictionaryService extends IService<DictionaryEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);
      /**
      * 字典表转换
      * @param obj
      */
     void dictionaryConvert(Object obj, HttpServletRequest request);

     /**
      * 获取会员等级所需的最低积分阈值
      * @param tier 等级编号（1, 2, 3...）
      * @return 该等级的最低积分要求；若字典表未配置则返回兼容默认值
      */
     double getMembershipThreshold(int tier);

     /**
      * 根据当前累计积分计算应属会员等级
      * @param totalPoints 用户当前总积分
      * @return 会员等级编号
      */
     int calculateMembershipTier(double totalPoints);
}