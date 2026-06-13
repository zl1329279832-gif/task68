package com.service.impl;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.utils.StringUtil;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.*;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import com.dao.DictionaryDao;
import com.entity.DictionaryEntity;
import com.service.DictionaryService;
import com.entity.view.DictionaryView;

/**
 * 字典 服务实现类
 */
@Service("dictionaryService")
@Transactional
public class DictionaryServiceImpl extends ServiceImpl<DictionaryDao, DictionaryEntity> implements DictionaryService {

    /**
     * 向后兼容的默认阈值：等级1最低积分、等级2最低积分、等级3最低积分
     * 当字典表（dic_code='huiyuandengji_threshold'）未配置时使用
     */
    private static final double[] DEFAULT_THRESHOLDS = {0.0, 10000.0, 100000.0, 1000000.0};

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        if(params != null && (params.get("limit") == null || params.get("page") == null)){
            params.put("page","1");
            params.put("limit","10");
        }
        Page<DictionaryView> page =new Query<DictionaryView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }

     /**
     * 赋值给字典表
     * @param obj view对象
     */
    public void dictionaryConvert(Object obj, HttpServletRequest request) {
        try {
            if (obj == null) return;
            //当前view和entity中的所有types的字段
            List<String> fieldNameList = new ArrayList<>();
            Class tempClass = obj.getClass();
            while (tempClass !=null) {
                Field[] declaredFields = tempClass.getDeclaredFields();
                for (Field f : declaredFields) {
                    f.setAccessible(true);
                    if (f.getType().getName().equals("java.lang.Integer") && f.getName().contains("Types")) {
                        fieldNameList.add(f.getName());
                    }
                }
                tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
            }

            // 获取监听器中的字典表
//            ServletContext servletContext = ContextLoader.getCurrentWebApplicationContext().getServletContext();
            ServletContext servletContext = request.getServletContext();
            Map<String, Map<Integer, String>> dictionaryMap= (Map<String, Map<Integer, String>>) servletContext.getAttribute("dictionaryMap");

            //通过Types的值给Value字段赋值
            for (String s : fieldNameList) {
                Field types = null;
                if(hasField(obj.getClass(),s)){
                    //判断view中有没有这个字段,有就通过反射取出字段
                    types= obj.getClass().getDeclaredField(s);//获取Types私有字段
                }else{
                    //本表中没有这个字段,说明它是父表中的字段,也就是entity中的字段,从entity中取值
                    types=obj.getClass().getSuperclass().getDeclaredField(s);
                }
                Field value = obj.getClass().getDeclaredField(s.replace("Types", "Value"));//获取value私有字段
                //设置权限
                types.setAccessible(true);
                value.setAccessible(true);

                //赋值
                if (StringUtil.isNotEmpty(String.valueOf(types.get(obj)))) { //types的值不为空
                    int i = Integer.parseInt(String.valueOf(types.get(obj)));//type
                    //把s1字符中的所有大写转小写,并在前面加 _
                    char[] chars = s.toCharArray();
                    StringBuffer sbf = new StringBuffer();
                    for(int  b=0; b< chars.length; b++){
                        char ch = chars[b];
                        if(ch <= 90 && ch >= 65){
                            sbf.append("_");
                            ch += 32;
                        }
                        sbf.append(ch);
                    }
                    String s2 = dictionaryMap.get(sbf.toString()).get(i);
                    value.set(obj, s2);
                } else {
                    new Exception("字典表赋值出现问题::::"+value.getName());
                    value.set(obj, "");
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 判断本实体有没有这个字段
     * @param c
     * @param fieldName
     * @return
     */
    public boolean hasField(Class c, String fieldName){
        Field[] fields = c.getDeclaredFields();

        for (Field f : fields) {
            if (fieldName.equals(f.getName())) {
                return true;

            }

        }

        return false;
    }

    /**
     * 获取指定会员等级的最低积分阈值
     * 从字典表 dic_code='huiyuandengji_threshold' 读取，code_index=等级编号，beizhu=阈值
     * 若字典表未配置，回退到兼容默认值
     */
    @Override
    public double getMembershipThreshold(int tier) {
        try {
            DictionaryEntity entity = this.selectOne(
                new EntityWrapper<DictionaryEntity>()
                    .eq("dic_code", "huiyuandengji_threshold")
                    .eq("code_index", tier)
            );
            if (entity != null && StringUtil.isNotEmpty(entity.getBeizhu())) {
                return Double.parseDouble(entity.getBeizhu());
            }
        } catch (Exception e) {
            // 字典表查询失败，使用默认值
        }
        // 回退默认值
        if (tier >= 0 && tier < DEFAULT_THRESHOLDS.length) {
            return DEFAULT_THRESHOLDS[tier];
        }
        return Double.MAX_VALUE;
    }

    /**
     * 根据累计积分计算会员等级
     * 从字典表读取各等级阈值，未配置时使用默认值（10000 / 100000 / 1000000）
     */
    @Override
    public int calculateMembershipTier(double totalPoints) {
        // 从高到低检查，找到第一个满足条件的等级
        // 先尝试从字典表读取最多等级数（默认支持3级）
        int maxTier = 3;
        for (int tier = maxTier; tier >= 1; tier--) {
            double threshold = getMembershipThreshold(tier);
            if (totalPoints >= threshold) {
                return tier;
            }
        }
        return 1;
    }

}
