package com.easy.admin.auth.service.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easy.admin.auth.common.constant.SessionConst;
import com.easy.admin.auth.common.status.SysDeptStatus;
import com.easy.admin.auth.common.status.SysUserStatus;
import com.easy.admin.auth.dao.SysUserMapper;
import com.easy.admin.auth.model.SysUser;
import com.easy.admin.auth.service.SysDeptService;
import com.easy.admin.auth.service.SysUserRoleService;
import com.easy.admin.auth.service.SysUserService;
import com.easy.admin.common.core.common.pagination.Page;
import com.easy.admin.common.core.common.status.CommonStatus;
import com.easy.admin.common.core.constant.CommonConst;
import com.easy.admin.common.core.exception.EasyException;
import com.easy.admin.common.redis.constant.RedisPrefix;
import com.easy.admin.common.redis.util.RedisUtil;
import com.easy.admin.config.shiro.service.ShiroService;
import com.easy.admin.exception.BusinessException;
import com.easy.admin.sys.common.constant.SexConst;
import com.easy.admin.sys.common.constant.SysConst;
import com.easy.admin.util.PasswordUtil;
import com.easy.admin.util.ShiroUtil;
import com.easy.admin.util.ToolUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 用户管理
 *
 * @author TengChongChong
 * @date 2018/12/25
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private ShiroService shiroService;

    @Autowired
    private SysDeptService sysDeptService;

    @Override
    public Page<SysUser> select(SysUser sysUser, Page<SysUser> page) {
        if (sysUser == null || StrUtil.isBlank(sysUser.getDeptId())) {
            // 不允许查询所有部门用户数据
            return null;
        }

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        if (Validator.isNotEmpty(sysUser.getUsername())) {
            queryWrapper.like("t.username", sysUser.getUsername());
        }
        if (Validator.isNotEmpty(sysUser.getNickname())) {
            queryWrapper.like("t.nickname", sysUser.getNickname());
        }
        if (Validator.isNotEmpty(sysUser.getPhoneNumber())) {
            queryWrapper.like("t.phone_number", sysUser.getPhoneNumber());
        }
        if (Validator.isNotEmpty(sysUser.getSource())) {
            queryWrapper.eq("t.source", sysUser.getSource());
        }
        if (Validator.isNotEmpty(sysUser.getSex())) {
            if (sysUser.getSex().contains(CommonConst.SPLIT)) {
                queryWrapper.in("t.sex", sysUser.getSex().split(CommonConst.SPLIT));
            } else {
                queryWrapper.eq("t.sex", sysUser.getSex());
            }
        }
        if (Validator.isNotEmpty(sysUser.getStatus())) {
            if (sysUser.getStatus().contains(CommonConst.SPLIT)) {
                queryWrapper.in("t.status", sysUser.getStatus().split(CommonConst.SPLIT));
            } else {
                queryWrapper.eq("t.status", sysUser.getStatus());
            }
        }
        if (Validator.isNotEmpty(sysUser.getDeptId())) {
            queryWrapper.eq("t.dept_id", sysUser.getDeptId());
        }
        page.setDefaultDesc("t.create_date");
        page.setRecords(baseMapper.select(page, queryWrapper));
        return page;
    }

    @Override
    public Page<SysUser> search(String keyword, String range, String deptId, Page<SysUser> page) {
        if (StrUtil.isBlank(keyword)) {
            throw new EasyException("请输入关键字");
        }
        if (StrUtil.isBlank(range)) {
            range = "all";
        }
        if ("currentDept".equals(range)) {
            deptId = ShiroUtil.getCurrentUser().getDeptId();
        }

        page.setRecords(baseMapper.search(page, "%" + keyword + "%", deptId, SysUserStatus.ENABLE.getCode(), SysDeptStatus.ENABLE.getCode(), CommonStatus.ENABLE.getCode()));
        return page;
    }

    @Override
    public List<SysUser> selectUsersByIds(String ids) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("u.id", ids.split(CommonConst.SPLIT));
        queryWrapper.eq("u.status", SysUserStatus.ENABLE.getCode());
        queryWrapper.eq("sd.status", SysDeptStatus.ENABLE.getCode());
        queryWrapper.eq("sdt.status", CommonStatus.ENABLE.getCode());
        return baseMapper.selectUsersByIds(queryWrapper);
    }

    @Override
    public SysUser get(String id) {
        ToolUtil.checkParams(id);
        SysUser sysUser = baseMapper.selectInfo(id);
        if (sysUser != null) {
            sysUser.setRoleIdList(baseMapper.selectRoles(id));
            if (Validator.isEmpty(sysUser.getRoleIdList())) {
                sysUser.setRoleIdList(Collections.emptyList());
            }
        }
        return sysUser;
    }

    @Override
    public SysUser add(String deptId) {
        ToolUtil.checkParams(deptId);
        SysUser sysUser = new SysUser();
        sysUser.setDeptId(deptId);
        sysUser.setStatus(SysUserStatus.ENABLE.getCode());
        sysUser.setSex(SexConst.BOY);
        sysUser.setRoleIdList(Collections.emptyList());
        return sysUser;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean remove(String ids) {
        ToolUtil.checkParams(ids);
        List<String> idList = Arrays.asList(ids.split(CommonConst.SPLIT));
        boolean isSuccess = removeByIds(idList);
        if (isSuccess) {
            // 删除分配给用户的权限
            sysUserRoleService.deleteUserRoleByUserIds(ids);
        }
        return isSuccess;
    }

    @Override
    public SysUser saveData(SysUser object, boolean updateAuthorization) {
        ToolUtil.checkParams(object);
        // 用户名不能重复
        if (checkHaving(object.getId(), "username", object.getUsername())) {
            throw new EasyException(BusinessException.USER_REGISTERED);
        }
        if (checkHaving(object.getId(), "email", object.getEmail())) {
            throw new EasyException("邮箱已注册");
        }
        if (checkHaving(object.getId(), "phone_number", object.getPhoneNumber())) {
            throw new EasyException("手机号已注册");
        }

        // 新增时密码如果为空,则使用默认密码
        if (StrUtil.isBlank(object.getId()) && Validator.isEmpty(object.getPassword())) {
            // 生成随机的盐
            object.setSalt(RandomUtil.randomString(10));
            object.setPassword(PasswordUtil.generatingPasswords(SysConst.projectProperties.getDefaultPassword(), object.getSalt()));
        } else if (Validator.isNotEmpty(object.getPassword())) {
            // 生成随机的盐
            object.setSalt(RandomUtil.randomString(10));
            object.setPassword(PasswordUtil.generatingPasswords(object.getPassword(), object.getSalt()));
        }
        if (Validator.isEmpty(object.getNickname())) {
            object.setNickname(object.getUsername());
        }

        boolean isSuccess = saveOrUpdate(object);
        if (isSuccess && updateAuthorization) {
            sysUserRoleService.saveUserRole(object.getId(), object.getRoleIdList());
            // 删除授权信息,下次请求资源重新授权
            RedisUtil.del(RedisPrefix.SHIRO_AUTHORIZATION + object);
        }
        return (SysUser) ToolUtil.checkResult(isSuccess, object);
    }

    /**
     * 检查数据是否已经存在
     *
     * @param id    数据id
     * @param field 字段
     * @param value 值
     * @return true/false
     */
    private boolean checkHaving(String id, String field, String value) {
        if (Validator.isNotEmpty(value)) {
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq(field, value);
            if (StrUtil.isNotBlank(id)) {
                queryWrapper.ne("id", id);
            }
            int count = baseMapper.selectCount(queryWrapper);
            return count > 0;
        }
        return false;
    }

    @Override
    public String resetPassword(String ids) {
        ToolUtil.checkParams(ids);
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        String defaultPassword = SysConst.projectProperties.getDefaultPassword();
        // 生成随机的盐
        String salt = RandomUtil.randomString(10);
        String password = PasswordUtil.generatingPasswords(defaultPassword, salt);
        queryWrapper.in("id", ids.split(CommonConst.SPLIT));
        boolean isSuccess = baseMapper.resetPassword(password, salt, queryWrapper) > 0;
        if (!isSuccess) {
            throw new EasyException("重置密码失败");
        }
        return defaultPassword;
    }

    @Override
    public boolean resetPassword(String username, String password) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        // 生成随机的盐
        String salt = RandomUtil.randomString(10);
        if (StrUtil.isBlank(password)) {
            password = PasswordUtil.generatingPasswords(SysConst.projectProperties.getDefaultPassword(), salt);
        } else {
            password = PasswordUtil.encryptedPasswords(password, salt);
        }
        queryWrapper.eq("username", username);
        return baseMapper.resetPassword(password, salt, queryWrapper) > 0;
    }

    @Override
    public boolean setStatus(String ids, String status) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", ids.split(CommonConst.SPLIT));
        int count = baseMapper.updateUserStatus(status, queryWrapper);
        return count > 0;
    }

    @Override
    public SysUser getSysUserByUserName(String username) {
        if (Validator.isNotEmpty(username)) {
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            return baseMapper.selectOne(queryWrapper);
        }
        return null;
    }

    @Override
    public SysUser getSysUserMailAndPhoneByUserName(String username) {
        if (Validator.isNotEmpty(username)) {
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("email, phone");
            queryWrapper.eq("username", username);
            return baseMapper.selectOne(queryWrapper);
        }
        return null;
    }

    @Override
    public boolean updateUserLastLoginDate(String userId) {
        SysUser sysUser = new SysUser();
        sysUser.setId(userId);
        sysUser.setLastLoginDate(new Date());
        return baseMapper.updateById(sysUser) > 0;
    }

    @Override
    public SysUser getCurrentUser() {
        SysUser sysUser = ShiroUtil.getCurrentUser();
        sysUser.setPassword(null);
        sysUser.setSalt(null);
        // 如果没有授权,从数据库查询权限
        if (sysUser.getPermissionList() == null) {
            sysUser = shiroService.queryUserPermissions(sysUser);
            ShiroUtil.setAttribute(SessionConst.USER_SESSION_KEY, sysUser);
        }
        // 由于密保邮箱&手机可能会发生变动,这里重新从数据库查询
        SysUser queryResult = selectEmailAndPhone(sysUser.getId());
        if (queryResult != null) {
            sysUser.setPhoneNumber(queryResult.getPhoneNumber());
            sysUser.setEmail(queryResult.getEmail());
        }
        return sysUser;
    }

    @Override
    public int countUser(String deptIds) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("dept_id", deptIds.split(CommonConst.SPLIT));
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public boolean updateAvatar(String url) {
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        SysUser sysUser = ShiroUtil.getCurrentUser();
        updateWrapper.set("avatar", url);
        updateWrapper.eq("id", sysUser.getId());
        return update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean setUserMail(String userId, String mail) {
        // 解绑该邮箱以前绑定的账号，防止一个邮箱绑定多个账号
        UpdateWrapper<SysUser> untyingMail = new UpdateWrapper<>();
        untyingMail.eq("email", mail);
        untyingMail.set("email", null);
        update(untyingMail);

        // 绑定新账号
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("email", mail);
        updateWrapper.eq("id", userId);
        return update(updateWrapper);
    }

    @Override
    public SysUser getUser(String id) {
        ToolUtil.checkParams(id);
        SysUser sysUser = baseMapper.selectInfo(id);
        if (sysUser != null) {
            sysUser.setDept(sysDeptService.get(sysUser.getDeptId()));
        }
        return sysUser;
    }

    @Override
    public Page<SysUser> selectUser(SysUser sysUser, Page<SysUser> page, boolean isSelect, String keywords) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        // 如果是查询用于显示已选择的用户列表，必须传入id
        boolean isInvalid = !isSelect && (sysUser == null || StrUtil.isBlank(sysUser.getId()));
        if (isInvalid) {
            return null;
        }
        if (sysUser != null) {
            if (Validator.isNotEmpty(sysUser.getUsername())) {
                queryWrapper.like("su.username", sysUser.getUsername());
            }
            if (Validator.isNotEmpty(sysUser.getNickname())) {
                queryWrapper.like("su.nickname", sysUser.getNickname());
            }
            if (Validator.isNotEmpty(sysUser.getDeptId())) {
                queryWrapper.eq("su.dept_id", sysUser.getDeptId());
            }
            if (Validator.isNotEmpty(sysUser.getRoleIdList())) {
                queryWrapper.in("sur.role_id", sysUser.getRoleIdList());
            }
        }

        if (StrUtil.isNotBlank(keywords)) {
            queryWrapper.and(i -> i.like("su.username", keywords).or().like("su.nickname", keywords));
        }
        if (sysUser != null && StrUtil.isNotBlank(sysUser.getId())) {
            if (sysUser.getId().contains(CommonConst.SPLIT)) {
                queryWrapper.in("su.id", sysUser.getId().split(CommonConst.SPLIT));
            } else {
                queryWrapper.eq("su.id", sysUser.getId());
            }
        }

        queryWrapper.eq("su.status", SysUserStatus.ENABLE.getCode());

        page.setRecords(baseMapper.selectUser(page, queryWrapper));
        return page;
    }

    @Override
    public SysUser selectPasswordAndSalt(String id) {
        return baseMapper.selectPasswordAndSalt(id);
    }

    @Override
    public SysUser selectEmailAndPhone(String id) {
        // 由于密保邮箱&手机可能会发生变动,这里重新从数据库查询
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("email", "phone_number");
        queryWrapper.eq("id", id);
        return getOne(queryWrapper);
    }

    @Override
    public boolean setPhone(String id, String phone) {
        UpdateWrapper<SysUser> setPhone = new UpdateWrapper<>();
        setPhone.eq("id", id);
        setPhone.set("phone_number", phone);
        return update(setPhone);
    }
}
