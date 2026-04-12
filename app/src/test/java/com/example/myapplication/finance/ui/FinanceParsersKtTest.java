package com.example.myapplication.finance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.myapplication.finance.model.TransactionType;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class FinanceParsersKtTest {

    @Test
    public void parseMbBankExpenseNotification_extractsExpectedFields() {
        String raw = "MB Bank • Bây giờ\n"
            + "Thông báo biến động số dư\n"
            + "TK 05xxx999|GD: -10,000VND 10/04/26 20:50 | SD: 401,224VND|DEN: NGUYEN VIET THANG - -43110001474353|ND: NGUYEN VIET THANG chuyen tien - Ma giao dich/ Trace 558880";

        NotificationDraft draft = FinanceParsersKt.parseNotificationText(raw, "com.mbmobile", "MB Bank");

        assertNotNull(draft);
        assertEquals(TransactionType.EXPENSE, draft.getType());
        assertEquals(10_000d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("MB Bank", draft.getSourceName());
        assertEquals("05xxx999", draft.getWalletHint());
        assertEquals("Chuyển khoản", draft.getCategory());
        assertTrue(draft.getNote().contains("MB Bank"));
        assertTrue(draft.getTransactionTimestampMillis() > 0L);

        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(draft.getTransactionTimestampMillis()),
            ZoneId.systemDefault()
        );
        assertEquals(2026, dateTime.getYear());
        assertEquals(4, dateTime.getMonthValue());
        assertEquals(10, dateTime.getDayOfMonth());
        assertEquals(20, dateTime.getHour());
        assertEquals(50, dateTime.getMinute());
    }

    @Test
    public void parseMbBankIncomeNotification_extractsExpectedFields() {
        String raw = "MB Bank • Bây giờ\n"
            + "Thông báo biến động số dư\n"
            + "TK 05xxx999|GD: +10,000VND 10/04/26 20:51 |SD: 411,224VND|TU: NGUYEN VIET THANG - 4311474353|ND: NGUYEN VIET THANG Chuyen tien- Ma GD ACSP/ az453273";

        NotificationDraft draft = FinanceParsersKt.parseNotificationText(raw, "com.mbmobile", "MB Bank");

        assertNotNull(draft);
        assertEquals(TransactionType.INCOME, draft.getType());
        assertEquals(10_000d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("05xxx999", draft.getWalletHint());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);
    }

    @Test
    public void parseMbBankNotification_withoutTuDen_stillParses() {
        String raw = "Thông báo biến động số dư: TK 05xxx999|GD: -25,000VND 10/04/26 22:01 |SD: 100,000VND|ND: Thanh toan tien dien";

        NotificationDraft draft = FinanceParsersKt.parseMbBankNotificationText(raw, "", "MB Bank");

        assertNotNull(draft);
        assertEquals(TransactionType.EXPENSE, draft.getType());
        assertEquals(25_000d, draft.getAmount(), 0.001d);
    }

    @Test
    public void parseBidvIncomeNotification_extractsExpectedFields() {
        String raw = "SmartBanking • Bây giờ\n"
            + "Thông báo BIDV\n"
            + "Thời gian giao dịch: 20:50 10/04/2026\n"
            + "Tài khoản thanh toán: 4311474353\n"
            + "Số tiền GD: +10,000 VND\n"
            + "Số dư cuối: 833,662 VND\n"
            + "Nội dung giao dịch: TKThe :0590900139999, tai MB. NGUYEN VIET THANG chuyen tien -CTLNHIDI000014950160814-1/1-CRE-002\n"
            + "Mã giao dịch: 083OCWK-88GSs28At";

        NotificationDraft draft = FinanceParsersKt.parseBidvNotificationText(raw, "com.bidv.smartbanking", "SmartBanking");

        assertNotNull(draft);
        assertEquals(TransactionType.INCOME, draft.getType());
        assertEquals(10_000d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("BIDV SmartBanking", draft.getSourceName());
        assertEquals("4311474353", draft.getWalletHint());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);
    }

    @Test
    public void parseBidvExpenseNotification_extractsExpectedFields() {
        String raw = "SmartBanking • Bây giờ\n"
            + "Thông báo BIDV\n"
            + "Thời gian giao dịch: 20:51 10/04/2026\n"
            + "Tài khoản thanh toán: 4311474353\n"
            + "Số tiền GD: -10,000 VND\n"
            + "Số dư cuối: 823,662 VND\n"
            + "Nội dung giao dịch: SMB-TkThe :0590900139999, tai MSCBVNVX. NGUYEN VIET THANG Chuyen tien- 0200970488041020512120263uaz453273\n"
            + "Mã giao dịch: 868295o4-88GSwUdBs";

        NotificationDraft draft = FinanceParsersKt.parseBidvNotificationText(raw, "com.bidv.smartbanking", "SmartBanking");

        assertNotNull(draft);
        assertEquals(TransactionType.EXPENSE, draft.getType());
        assertEquals(10_000d, draft.getAmount(), 0.001d);
        assertEquals("4311474353", draft.getWalletHint());
    }

    @Test
    public void parseVietcombankExpenseNotification_extractsExpectedFields() {
        String raw = "Số dư TK VCB 9344928117 -260,590 VND lúc 06-04-2026 15:29:18."
            + "Số dư 270,345 VND. Ref MBVCB.13670620003.484467.NV10171Q02726040615263117."
            + "CT tu 9344928117 HOANG VAN TAI toi V4TDQTM10171 SIEU THI THANH DO 497 QUANG TRUNG tai BIDV";

        NotificationDraft draft = FinanceParsersKt.parseVietcombankNotificationText(
            raw,
            "com.vcb.app",
            "Vietcombank"
        );

        assertNotNull(draft);
        assertEquals(TransactionType.EXPENSE, draft.getType());
        assertEquals(260_590d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("Vietcombank", draft.getSourceName());
        assertEquals("9344928117", draft.getWalletHint());
        assertEquals("Chuyển khoản", draft.getCategory());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);

        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(draft.getTransactionTimestampMillis()),
            ZoneId.systemDefault()
        );
        assertEquals(2026, dateTime.getYear());
        assertEquals(4, dateTime.getMonthValue());
        assertEquals(6, dateTime.getDayOfMonth());
        assertEquals(15, dateTime.getHour());
        assertEquals(29, dateTime.getMinute());
    }

    @Test
    public void parseVietcombankIncomeNotification_extractsExpectedFields() {
        String raw = "Số dư TK VCB 9344928117 +310,000 VND lúc 05-04-2026 11:53:17. "
            + "Số dư 530,935 VND. Ref NGUYEN VAN THANH chuyen tien#SP#020097042204051153162026A3UR251905.5189.94571.115317";

        NotificationDraft draft = FinanceParsersKt.parseVietcombankNotificationText(
            raw,
            "com.vcb.app",
            "Vietcombank"
        );

        assertNotNull(draft);
        assertEquals(TransactionType.INCOME, draft.getType());
        assertEquals(310_000d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("9344928117", draft.getWalletHint());
        assertEquals("Thu chuyển khoản", draft.getCategory());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);
    }

    @Test
    public void parseVietinExpenseNotification_extractsExpectedFields() {
        String raw = "Thời gian: 09/04/2026 16:52\n"
            + "Tài khoản: 109876995577\n"
            + "Giao dịch: -1,120,741 VND\n"
            + "Số dư hiện tại: 383,581 VND\n"
            + "Nội dung: ShopeePay 84916641674 - VietinBank - 5604090nfnf5";

        NotificationDraft draft = FinanceParsersKt.parseVietinBankNotificationText(
            raw,
            "com.vietinbank.app",
            "VietinBank iPay"
        );

        assertNotNull(draft);
        assertEquals(TransactionType.EXPENSE, draft.getType());
        assertEquals(1_120_741d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("VietinBank", draft.getSourceName());
        assertEquals("109876995577", draft.getWalletHint());
        assertEquals("Chi khác", draft.getCategory());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);

        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(draft.getTransactionTimestampMillis()),
            ZoneId.systemDefault()
        );
        assertEquals(2026, dateTime.getYear());
        assertEquals(4, dateTime.getMonthValue());
        assertEquals(9, dateTime.getDayOfMonth());
        assertEquals(16, dateTime.getHour());
        assertEquals(52, dateTime.getMinute());
    }

    @Test
    public void parseVietinIncomeNotification_extractsExpectedFields() {
        String raw = "Thời gian: 09/04/2026 16:51\n"
            + "Tài khoản: 109876995577\n"
            + "Giao dịch: +1,200,000 VND\n"
            + "Số dư hiện tại: 1,504,322 VND\n"
            + "Nội dung: CT DEN:284T2640DUE9EQUC DINH THI THU Chuyen tien (0916641674)";

        NotificationDraft draft = FinanceParsersKt.parseVietinBankNotificationText(
            raw,
            "com.vietinbank.app",
            "VietinBank iPay"
        );

        assertNotNull(draft);
        assertEquals(TransactionType.INCOME, draft.getType());
        assertEquals(1_200_000d, draft.getAmount(), 0.001d);
        assertEquals("VND", draft.getCurrency());
        assertEquals("109876995577", draft.getWalletHint());
        assertEquals("Thu chuyển khoản", draft.getCategory());
        assertTrue(draft.getTransactionTimestampMillis() > 0L);
    }

    @Test
    public void parseGenericNotification_keepsFallbackBehavior() {
        NotificationDraft draft = FinanceParsersKt.parseNotificationText(
            "Ví điện tử ABC: Bạn vừa nhận +50,000đ từ Nguyễn Văn A",
            "com.abc.wallet",
            "ABC Wallet"
        );

        assertNotNull(draft);
        assertEquals(TransactionType.INCOME, draft.getType());
        assertEquals(50_000d, draft.getAmount(), 0.001d);
    }
}
