
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 商品订单
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/changpianOrder")
public class ChangpianOrderController {
    private static final Logger logger = LoggerFactory.getLogger(ChangpianOrderController.class);

    @Autowired
    private ChangpianOrderService changpianOrderService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;

    //级联表service
    @Autowired
    private AddressService addressService;
    @Autowired
    private ChangpianService changpianService;
    @Autowired
    private YonghuService yonghuService;
@Autowired
private CartService cartService;
@Autowired
private ChangpianCommentbackService changpianCommentbackService;



    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永不会进入");
        else if("用户".equals(role))
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = changpianOrderService.queryPage(params);

        //字典表数据转换
        List<ChangpianOrderView> list =(List<ChangpianOrderView>)page.getList();
        for(ChangpianOrderView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        ChangpianOrderEntity changpianOrder = changpianOrderService.selectById(id);
        if(changpianOrder !=null){
            //entity转view
            ChangpianOrderView view = new ChangpianOrderView();
            BeanUtils.copyProperties( changpianOrder , view );//把实体数据重构到view中

                //级联表
                AddressEntity address = addressService.selectById(changpianOrder.getAddressId());
                if(address != null){
                    BeanUtils.copyProperties( address , view ,new String[]{ "id", "createTime", "insertTime", "updateTime", "yonghuId"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setAddressId(address.getId());
                    view.setAddressYonghuId(address.getYonghuId());
                }
                //级联表
                ChangpianEntity changpian = changpianService.selectById(changpianOrder.getChangpianId());
                if(changpian != null){
                    BeanUtils.copyProperties( changpian , view ,new String[]{ "id", "createTime", "insertTime", "updateTime"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setChangpianId(changpian.getId());
                }
                //级联表
                YonghuEntity yonghu = yonghuService.selectById(changpianOrder.getYonghuId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createTime", "insertTime", "updateTime"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setYonghuId(yonghu.getId());
                }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody ChangpianOrderEntity changpianOrder, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,changpianOrder:{}",this.getClass().getName(),changpianOrder.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永远不会进入");
        else if("用户".equals(role))
            changpianOrder.setYonghuId(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId"))));

        changpianOrder.setInsertTime(new Date());
        changpianOrder.setCreateTime(new Date());
        changpianOrderService.insert(changpianOrder);
        return R.ok();
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody ChangpianOrderEntity changpianOrder, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,changpianOrder:{}",this.getClass().getName(),changpianOrder.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
//        if(false)
//            return R.error(511,"永远不会进入");
//        else if("用户".equals(role))
//            changpianOrder.setYonghuId(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId"))));
        //根据字段查询是否有相同数据
        Wrapper<ChangpianOrderEntity> queryWrapper = new EntityWrapper<ChangpianOrderEntity>()
            .eq("id",0)
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        ChangpianOrderEntity changpianOrderEntity = changpianOrderService.selectOne(queryWrapper);
        if(changpianOrderEntity==null){
            changpianOrderService.updateById(changpianOrder);//根据id更新
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        changpianOrderService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        try {
            List<ChangpianOrderEntity> changpianOrderList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("static/upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            ChangpianOrderEntity changpianOrderEntity = new ChangpianOrderEntity();
//                            changpianOrderEntity.setChangpianOrderUuidNumber(data.get(0));                    //订单号 要改的
//                            changpianOrderEntity.setAddressId(Integer.valueOf(data.get(0)));   //送货地址 要改的
//                            changpianOrderEntity.setChangpianId(Integer.valueOf(data.get(0)));   //商品 要改的
//                            changpianOrderEntity.setYonghuId(Integer.valueOf(data.get(0)));   //用户 要改的
//                            changpianOrderEntity.setBuyNumber(Integer.valueOf(data.get(0)));   //购买数量 要改的
//                            changpianOrderEntity.setChangpianOrderCourierNumber(data.get(0));                    //快递单号 要改的
//                            changpianOrderEntity.setChangpianOrderCourierName(data.get(0));                    //快递公司 要改的
//                            changpianOrderEntity.setChangpianOrderTruePrice(data.get(0));                    //实付价格 要改的
//                            changpianOrderEntity.setChangpianOrderTypes(Integer.valueOf(data.get(0)));   //订单类型 要改的
//                            changpianOrderEntity.setChangpianOrderPaymentTypes(Integer.valueOf(data.get(0)));   //支付类型 要改的
//                            changpianOrderEntity.setInsertTime(date);//时间
//                            changpianOrderEntity.setCreateTime(date);//时间
                            changpianOrderList.add(changpianOrderEntity);


                            //把要查询是否重复的字段放入map中
                                //订单号
                                if(seachFields.containsKey("changpianOrderUuidNumber")){
                                    List<String> changpianOrderUuidNumber = seachFields.get("changpianOrderUuidNumber");
                                    changpianOrderUuidNumber.add(data.get(0));//要改的
                                }else{
                                    List<String> changpianOrderUuidNumber = new ArrayList<>();
                                    changpianOrderUuidNumber.add(data.get(0));//要改的
                                    seachFields.put("changpianOrderUuidNumber",changpianOrderUuidNumber);
                                }
                        }

                        //查询是否重复
                         //订单号
                        List<ChangpianOrderEntity> changpianOrderEntities_changpianOrderUuidNumber = changpianOrderService.selectList(new EntityWrapper<ChangpianOrderEntity>().in("changpian_order_uuid_number", seachFields.get("changpianOrderUuidNumber")));
                        if(changpianOrderEntities_changpianOrderUuidNumber.size() >0 ){
                            ArrayList<String> repeatFields = new ArrayList<>();
                            for(ChangpianOrderEntity s:changpianOrderEntities_changpianOrderUuidNumber){
                                repeatFields.add(s.getChangpianOrderUuidNumber());
                            }
                            return R.error(511,"数据库的该表中的 [订单号] 字段已经存在 存在数据为:"+repeatFields.toString());
                        }
                        changpianOrderService.insertBatch(changpianOrderList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }





    /**
    * 前端列表
    */
    @IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("list方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));

        // 没有指定排序字段就默认id倒序
        if(StringUtil.isEmpty(String.valueOf(params.get("orderBy")))){
            params.put("orderBy","id");
        }
        PageUtils page = changpianOrderService.queryPage(params);

        //字典表数据转换
        List<ChangpianOrderView> list =(List<ChangpianOrderView>)page.getList();
        for(ChangpianOrderView c:list)
            dictionaryService.dictionaryConvert(c, request); //修改对应字典表字段
        return R.ok().put("data", page);
    }

    /**
    * 前端详情
    */
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("detail方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        ChangpianOrderEntity changpianOrder = changpianOrderService.selectById(id);
            if(changpianOrder !=null){


                //entity转view
                ChangpianOrderView view = new ChangpianOrderView();
                BeanUtils.copyProperties( changpianOrder , view );//把实体数据重构到view中

                //级联表
                    AddressEntity address = addressService.selectById(changpianOrder.getAddressId());
                if(address != null){
                    BeanUtils.copyProperties( address , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setAddressId(address.getId());
                }
                //级联表
                    ChangpianEntity changpian = changpianService.selectById(changpianOrder.getChangpianId());
                if(changpian != null){
                    BeanUtils.copyProperties( changpian , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setChangpianId(changpian.getId());
                }
                //级联表
                    YonghuEntity yonghu = yonghuService.selectById(changpianOrder.getYonghuId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createDate"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setYonghuId(yonghu.getId());
                }
                //修改对应字典表字段
                dictionaryService.dictionaryConvert(view, request);
                return R.ok().put("data", view);
            }else {
                return R.error(511,"查不到数据");
            }
    }


    /**
    * 前端保存
    */
    @RequestMapping("/add")
    public R add(@RequestBody ChangpianOrderEntity changpianOrder, HttpServletRequest request){
        logger.debug("add方法:,,Controller:{},,changpianOrder:{}",this.getClass().getName(),changpianOrder.toString());
            ChangpianEntity changpianEntity = changpianService.selectById(changpianOrder.getChangpianId());
            if(changpianEntity == null){
                return R.error(511,"查不到该商品");
            }
            // Double changpianNewMoney = changpianEntity.getChangpianNewMoney();

            if(false){
            }
            else if((changpianEntity.getChangpianKucunNumber() -changpianOrder.getBuyNumber())<0){
                return R.error(511,"购买数量不能大于库存数量");
            }
            else if(changpianEntity.getChangpianNewMoney() == null){
                return R.error(511,"商品价格不能为空");
            }

            //计算所获得积分
            Double buyJifen =0.0;
            Integer userId = (Integer) request.getSession().getAttribute("userId");
            changpianOrder.setChangpianOrderTypes(3); //设置订单状态为已支付
            changpianOrder.setChangpianOrderTruePrice(0.0); //设置实付价格
            changpianOrder.setYonghuId(userId); //设置订单支付人id
            changpianOrder.setChangpianOrderUuidNumber(String.valueOf(new Date().getTime()));
            changpianOrder.setChangpianOrderPaymentTypes(1);
            changpianOrder.setInsertTime(new Date());
            changpianOrder.setCreateTime(new Date());
                changpianEntity.setChangpianKucunNumber( changpianEntity.getChangpianKucunNumber() -changpianOrder.getBuyNumber());
                changpianService.updateById(changpianEntity);
                changpianOrderService.insert(changpianOrder);//新增订单
            return R.ok();
    }
    /**
     * 添加订单
     * 整段事务化（在 Service 层），悲观锁防超卖，会员等级阈值可配置
     */
    @RequestMapping("/order")
    public R add(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("order方法:,,Controller:{},,params:{}",this.getClass().getName(),params.toString());

        // 获取当前登录用户的id
        Integer userId = (Integer) request.getSession().getAttribute("userId");
        Integer addressId = Integer.valueOf(String.valueOf(params.get("addressId")));
        Integer changpianOrderPaymentTypes = Integer.valueOf(String.valueOf(params.get("changpianOrderPaymentTypes")));

        String data = String.valueOf(params.get("changpians"));
        JSONArray jsonArray = JSON.parseArray(data);
        List<Map> changpians = JSON.parseObject(jsonArray.toString(), List.class);

        // 委托给 Service 层（单事务，悲观锁防超卖）
        return changpianOrderService.placeOrder(userId, addressId, changpianOrderPaymentTypes, changpians, request);
    }











    /**
    * 退款
    * 事务化回滚：余额、积分、会员等级、库存、订单状态一起回滚
    * 只允许合法前置状态（已支付 / 已发货）
    */
    @RequestMapping("/refund")
    public R refund(Integer id, HttpServletRequest request){
        logger.debug("refund方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        Integer userId = (Integer) request.getSession().getAttribute("userId");
        return changpianOrderService.refundOrder(id, userId, request);
    }


    /**
     * 发货
     * 仅允许"已支付/待发货(3)"状态执行发货
     */
    @RequestMapping("/deliver")
    public R deliver(Integer id ,String changpianOrderCourierNumber, String changpianOrderCourierName){
        logger.debug("deliver:,,Controller:{},,id:{}",this.getClass().getName(),id.toString());
        return changpianOrderService.deliverOrder(id, changpianOrderCourierNumber, changpianOrderCourierName);
    }









    /**
     * 收货
     * 仅允许"已发货(4)"状态执行收货
     */
    @RequestMapping("/receiving")
    public R receiving(Integer id){
        logger.debug("receiving:,,Controller:{},,id:{}",this.getClass().getName(),id.toString());
        return changpianOrderService.receiveOrder(id);
    }



    /**
    * 评价
    */
    @RequestMapping("/commentback")
    public R commentback(Integer id, String commentbackText, Integer changpianCommentbackPingfenNumber, HttpServletRequest request){
        logger.debug("commentback方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
            ChangpianOrderEntity changpianOrder = changpianOrderService.selectById(id);
        if(changpianOrder == null)
            return R.error(511,"查不到该订单");
        if(changpianOrder.getChangpianOrderTypes() != 5)
            return R.error(511,"您不能评价");
        Integer changpianId = changpianOrder.getChangpianId();
        if(changpianId == null)
            return R.error(511,"查不到该商品");

        ChangpianCommentbackEntity changpianCommentbackEntity = new ChangpianCommentbackEntity();
            changpianCommentbackEntity.setId(id);
            changpianCommentbackEntity.setChangpianId(changpianId);
            changpianCommentbackEntity.setYonghuId((Integer) request.getSession().getAttribute("userId"));
            changpianCommentbackEntity.setChangpianCommentbackText(commentbackText);
            changpianCommentbackEntity.setReplyText(null);
            changpianCommentbackEntity.setInsertTime(new Date());
            changpianCommentbackEntity.setUpdateTime(null);
            changpianCommentbackEntity.setCreateTime(new Date());
            changpianCommentbackService.insert(changpianCommentbackEntity);

            changpianOrder.setChangpianOrderTypes(1);//设置订单状态为已评价
            changpianOrderService.updateById(changpianOrder);//根据id更新
            return R.ok();
    }







}
