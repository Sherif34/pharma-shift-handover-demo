package edu.cse454.pharma;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import edu.cse454.pharma.ShiftHandoverService.Shift;
import edu.cse454.pharma.ShiftHandoverService.ShiftRepository;
import edu.cse454.pharma.ShiftHandoverService.TenderTotals;
import edu.cse454.pharma.ShiftHandoverService.Variance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static edu.cse454.pharma.ShiftHandoverService.Difference.MATCH;
import static edu.cse454.pharma.ShiftHandoverService.Difference.SHORTAGE;
import static edu.cse454.pharma.ShiftHandoverService.Difference.SURPLUS;
import static edu.cse454.pharma.ShiftHandoverService.Status.CLOSED;
import static edu.cse454.pharma.ShiftHandoverService.Status.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftHandoverServiceTest {
    private static final String SHIFT = "SHIFT-01";
    private static final String CASHIER = "cashier-demo";
    private static final TenderTotals EXPECTED = amounts("1000", "500");

    @Mock ShiftRepository repository;
    private ShiftHandoverService service;

    @BeforeEach
    void setUp() {
        service = new ShiftHandoverService(repository);
    }

    @Test
    void hidesExpectedCashAndVisaBeforeSubmission() {
        stubOpenShift();

        var form = service.openForm(SHIFT, CASHIER);

        assertEquals(SHIFT, form.shiftId());
        assertEquals(OPEN, form.status());
        assertEquals(List.of("shiftId", "status"), Arrays.stream(form.getClass().getRecordComponents())
                .map(RecordComponent::getName).toList());
        verify(repository).findById(SHIFT);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void showsCashShortageOnlyAfterSubmittingTheCount() {
        stubOpenShift();
        var counted = amounts("950", "500");
        when(repository.closeIfOpenAndOwned(SHIFT, CASHIER, counted))
                .thenReturn(Optional.of(EXPECTED));

        var receipt = service.submit(SHIFT, CASHIER, counted);

        assertEquals(CLOSED, receipt.status());
        assertEquals(new Variance(SHORTAGE, new BigDecimal("50")), receipt.cash());
        assertEquals(new Variance(MATCH, BigDecimal.ZERO), receipt.visa());
        verify(repository).closeIfOpenAndOwned(SHIFT, CASHIER, counted);
    }

    @Test
    void comparesCashAndVisaIndependently() {
        stubOpenShift();
        var counted = amounts("1050", "480");
        when(repository.closeIfOpenAndOwned(SHIFT, CASHIER, counted))
                .thenReturn(Optional.of(EXPECTED));

        var receipt = service.submit(SHIFT, CASHIER, counted);

        assertEquals(new Variance(SURPLUS, new BigDecimal("50")), receipt.cash());
        assertEquals(new Variance(SHORTAGE, new BigDecimal("20")), receipt.visa());
    }

    @Test
    void rejectsAnotherCashiersSubmission() {
        stubOpenShift();

        assertThrows(SecurityException.class,
                () -> service.submit(SHIFT, "another-cashier", amounts("950", "500")));

        verify(repository).findById(SHIFT);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsSubmissionForAnUnknownShift() {
        when(repository.findById(SHIFT)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.submit(SHIFT, CASHIER, amounts("950", "500")));

        verify(repository).findById(SHIFT);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsNegativeCountBeforeReadingTheShift() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(SHIFT, CASHIER, amounts("-1", "500")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(SHIFT, CASHIER, amounts("950", "-1")));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsSubmissionIfTheShiftClosesWhileCashierIsSubmitting() {
        stubOpenShift();
        var counted = amounts("950", "500");
        when(repository.closeIfOpenAndOwned(SHIFT, CASHIER, counted)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.submit(SHIFT, CASHIER, counted));
    }

    @Test
    void locksClosedShiftAndKeepsOriginalCountOnRetry() {
        var repository = new InMemoryShiftRepository(new Shift(SHIFT, CASHIER, OPEN), EXPECTED);
        var handover = new ShiftHandoverService(repository);
        var originalCount = amounts("950", "500");

        handover.submit(SHIFT, CASHIER, originalCount);

        assertEquals(CLOSED, repository.findById(SHIFT).orElseThrow().status());
        assertThrows(IllegalStateException.class, () -> handover.openForm(SHIFT, CASHIER));
        assertThrows(IllegalStateException.class, () -> handover.submit(SHIFT, CASHIER, EXPECTED));
        assertEquals(originalCount, repository.submittedCount().orElseThrow());
    }

    private void stubOpenShift() {
        when(repository.findById(SHIFT)).thenReturn(Optional.of(new Shift(SHIFT, CASHIER, OPEN)));
    }

    private static TenderTotals amounts(String cash, String visa) {
        return new TenderTotals(new BigDecimal(cash), new BigDecimal(visa));
    }
}
