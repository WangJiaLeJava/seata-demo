package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountTCCServiceImpl implements AccountTCCService {
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private AccountFreezeMapper freezeMapper;

    @Override
    @Transactional
    public void deduct(String userId, int money) {
        String xid = RootContext.getXID();
        //幂等
        AccountFreeze oldFreeze = freezeMapper.selectById(xid);
        if (oldFreeze !=null){
            return;
        }
        accountMapper.deduct(userId,money);
        AccountFreeze freeze=new AccountFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.TRY);
        freeze.setXid(xid);
        freezeMapper.insert(freeze);
    }
    @Override
    public boolean confirm(BusinessActionContext context) {
        String xid = context.getXid();
        int count = freezeMapper.deleteById(xid);
        return count==1;
    }
    @Override
    public boolean cancel(BusinessActionContext context) {
        String xid = context.getXid();
        String userId = context.getActionContext("userId").toString();
        AccountFreeze freeze = freezeMapper.selectById(xid);
        if (freeze==null){
            freeze=new AccountFreeze();
            freeze.setUserId(userId);
            freeze.setFreezeMoney(0);
            freeze.setState(AccountFreeze.State.CANCEL);
            freeze.setXid(xid);
            freezeMapper.insert(freeze);
            return true;
        }
        if (freeze.getState()==AccountFreeze.State.CANCEL){
            return true;
        }
        //恢复金额
        accountMapper.refund(freeze.getUserId(),freeze.getFreezeMoney());
        //将冻结金额清零
        freeze.setFreezeMoney(0);
        freeze.setState(AccountFreeze.State.CANCEL);
        log.info("{}",freeze);
        int count = freezeMapper.updateById(freeze);
        return count==1;
    }
}
