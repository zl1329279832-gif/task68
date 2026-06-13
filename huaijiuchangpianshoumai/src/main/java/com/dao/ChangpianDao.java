package com.dao;

import com.entity.ChangpianEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.view.ChangpianView;

/**
 * 商品 Dao 接口
 *
 * @author 
 */
public interface ChangpianDao extends BaseMapper<ChangpianEntity> {

   List<ChangpianView> selectListView(Pagination page,@Param("params")Map<String,Object> params);

   /**
    * 悲观锁查询商品（SELECT ... FOR UPDATE），防止并发超卖
    */
   ChangpianEntity selectForUpdate(@Param("id") Integer id);

}
