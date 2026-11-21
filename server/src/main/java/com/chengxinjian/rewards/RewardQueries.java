package com.chengxinjian.rewards;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.catalog.CategoryModels.Page;
import com.chengxinjian.common.BusinessException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;

final class RewardQueries {
    private RewardQueries() {}
    static void global(AdminPrincipal admin) {
        if (!"all".equals(admin.dataScope())) throw BusinessException.forbidden();
    }
    static BusinessException conflict(String code,String message) {
        return new BusinessException(HttpStatus.CONFLICT,code,message);
    }
    static <T> Page<T> page(JdbcTemplate jdbc,String from,List<?> args,RowMapper<T> mapper,int page,int size) {
        long total=jdbc.queryForObject("SELECT COUNT(*) "+from,Long.class,args.toArray());
        var params=new ArrayList<Object>(args);
        params.add(size);params.add(((long)page-1)*size);
        return new Page<>(jdbc.query("SELECT r.* "+from+" ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?",mapper,params.toArray()),page,size,total);
    }
}
