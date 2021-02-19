package com.github.unidbg.arm.backend.kvm;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.*;
import com.github.unidbg.pointer.UnidbgPointer;
import keystone.Keystone;
import keystone.KeystoneArchitecture;
import keystone.KeystoneEncoded;
import keystone.KeystoneMode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.ArmConst;
import unicorn.Unicorn;
import unicorn.UnicornConst;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class KvmBackend32 extends KvmBackend {

    private static final Log log = LogFactory.getLog(KvmBackend32.class);

    public KvmBackend32(Emulator<?> emulator, Kvm kvm) throws BackendException {
        super(emulator, kvm);
    }

    @Override
    public void onInitialize() {
        super.onInitialize();

        mem_map(REG_VBAR_EL1, getPageSize(), UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_EXEC);
        ByteBuffer buffer = ByteBuffer.allocate(getPageSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            if (buffer.position() == 0x600) {
                buffer.putInt(0x390003e0); // strb w0, [sp]
            } else {
                buffer.putInt(0x390003e1); // strb w1, [sp]
            }
            if (buffer.hasRemaining()) {
                buffer.putInt(0xd69f03e0); // eret
            }
        }
        UnidbgPointer ptr = UnidbgPointer.pointer(emulator, REG_VBAR_EL1);
        assert ptr != null;
        ptr.write(buffer.array());
    }

    @Override
    public boolean handleException(long esr, long far, long elr, long spsr, long pc) {
        int ec = (int) ((esr >> 26) & 0x3f);
        if (log.isDebugEnabled()) {
            log.debug("handleException syndrome=0x" + Long.toHexString(esr) + ", far=0x" + Long.toHexString(far) + ", elr=0x" + Long.toHexString(elr) + ", ec=0x" + Integer.toHexString(ec) + ", pc=0x" + Long.toHexString(pc));
        }

        switch (ec) {
            case EC_DATAABORT:
            case EC_INSNABORT:
            default:
                throw new UnsupportedOperationException("handleException ec=0x" + Integer.toHexString(ec));
        }
    }

    @Override
    public void switchUserMode() {
    }

    @Override
    public void enableVFP() {
        reg_write(ArmConst.UC_ARM_REG_FPEXC, 0x40000000);
    }

    @Override
    public Number reg_read(int regId) throws BackendException {
        try {
            switch (regId) {
                case ArmConst.UC_ARM_REG_R0:
                case ArmConst.UC_ARM_REG_R1:
                case ArmConst.UC_ARM_REG_R2:
                case ArmConst.UC_ARM_REG_R3:
                case ArmConst.UC_ARM_REG_R4:
                case ArmConst.UC_ARM_REG_R5:
                case ArmConst.UC_ARM_REG_R6:
                case ArmConst.UC_ARM_REG_R7:
                case ArmConst.UC_ARM_REG_R8:
                case ArmConst.UC_ARM_REG_R9:
                case ArmConst.UC_ARM_REG_R10:
                case ArmConst.UC_ARM_REG_R11:
                case ArmConst.UC_ARM_REG_R12:
                    return (int) (kvm.reg_read64(regId - ArmConst.UC_ARM_REG_R0) & 0xffffffffL);
                case ArmConst.UC_ARM_REG_SP:
                    return kvm.reg_read_sp64() & 0xffffffffL;
                case ArmConst.UC_ARM_REG_LR:
                    return kvm.reg_read64(14);
                case ArmConst.UC_ARM_REG_PC:
                    return kvm.reg_read_pc64();
                case ArmConst.UC_ARM_REG_CPSR:
                    return kvm.reg_read_nzcv();
                default:
                    throw new KvmException("regId=" + regId);
            }
        } catch (KvmException e) {
            throw new BackendException(e);
        }
    }

    @Override
    public void reg_write(int regId, Number value) throws BackendException {
        try {
            switch (regId) {
                case ArmConst.UC_ARM_REG_R0:
                case ArmConst.UC_ARM_REG_R1:
                case ArmConst.UC_ARM_REG_R2:
                case ArmConst.UC_ARM_REG_R3:
                    kvm.reg_write64(regId - ArmConst.UC_ARM_REG_R0, value.longValue() & 0xffffffffL);
                    break;
                case ArmConst.UC_ARM_REG_SP:
                    kvm.reg_set_sp64(value.longValue() & 0xffffffffL);
                    break;
                case ArmConst.UC_ARM_REG_LR:
                    kvm.reg_write64(14, value.longValue() & 0xffffffffL);
                    break;
                case ArmConst.UC_ARM_REG_FPEXC:
                    kvm.reg_set_fpexc(value.longValue() & 0xffffffffL);
                    break;
                case ArmConst.UC_ARM_REG_C13_C0_3:
                    kvm.reg_set_tpidrro_el0(value.longValue());
                    break;
                default:
                    throw new KvmException("regId=" + regId);
            }
        } catch (KvmException e) {
            throw new BackendException(e);
        }
    }

    @Override
    public byte[] reg_read_vector(int regId) throws BackendException {
        return null;
    }

    @Override
    public void reg_write_vector(int regId, byte[] vector) throws BackendException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Unicorn.UnHook hook_add_new(CodeHook callback, long begin, long end, Object user_data) throws BackendException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Unicorn.UnHook debugger_add(DebugHook callback, long begin, long end, Object user_data) throws BackendException {
        return null;
    }

    @Override
    public void hook_add_new(ReadHook callback, long begin, long end, Object user_data) throws BackendException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void hook_add_new(WriteHook callback, long begin, long end, Object user_data) throws BackendException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Unicorn.UnHook hook_add_new(BlockHook callback, long begin, long end, Object user_data) throws BackendException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected byte[] addSoftBreakPoint(long address, int svcNumber, boolean thumb) {
        try (Keystone keystone = new Keystone(KeystoneArchitecture.Arm, thumb ? KeystoneMode.ArmThumb : KeystoneMode.Arm)) {
            KeystoneEncoded encoded = keystone.assemble("bkpt #" + svcNumber);
            return encoded.getMachineCode();
        }
    }

}