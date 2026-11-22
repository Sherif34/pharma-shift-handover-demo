package edu.cse454.pharma;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class ShiftHandoverService {
    public enum Status { OPEN, CLOSED }
    public enum Difference { SHORTAGE, MATCH, SURPLUS }

    public record Shift(String id, String cashierId, Status status) {}

    public record TenderTotals(BigDecimal cash, BigDecimal visa) {
        public TenderTotals {
            if (cash == null || visa == null || cash.signum() < 0 || visa.signum() < 0) {
                throw new IllegalArgumentException("Enter nonnegative amounts for cash and Visa.");
            }
        }
    }

    public record CashierForm(String shiftId, Status status) {}
    public record Variance(Difference result, BigDecimal amount) {}
    public record Receipt(String shiftId, Status status, Variance cash, Variance visa) {}

    public interface ShiftRepository {
        Optional<Shift> findById(String shiftId);

        // Record the count and close the shift atomically. Return the expected
        // closing totals only when that transition succeeds.
        Optional<TenderTotals> closeIfOpenAndOwned(
                String shiftId, String authenticatedCashierId, TenderTotals counted);
    }

    private final ShiftRepository shifts;

    public ShiftHandoverService(ShiftRepository shifts) {
        this.shifts = Objects.requireNonNull(shifts);
    }

    public CashierForm openForm(String shiftId, String authenticatedCashierId) {
        var shift = requireOwnOpenShift(shiftId, authenticatedCashierId);
        return new CashierForm(shift.id(), shift.status());
    }

    public Receipt submit(String shiftId, String authenticatedCashierId, TenderTotals counted) {
        Objects.requireNonNull(counted, "Counted totals are required.");
        var shift = requireOwnOpenShift(shiftId, authenticatedCashierId);
        var expected = shifts.closeIfOpenAndOwned(shift.id(), authenticatedCashierId, counted)
                .orElseThrow(() -> new IllegalStateException("The shift can no longer be closed."));

        return new Receipt(shift.id(), Status.CLOSED,
                calculateVariance(counted.cash(), expected.cash()),
                calculateVariance(counted.visa(), expected.visa()));
    }

    private Shift requireOwnOpenShift(String shiftId, String cashierId) {
        var shift = shifts.findById(shiftId)
                .orElseThrow(() -> new NoSuchElementException("Shift was not found."));
        if (!shift.cashierId().equals(cashierId)) {
            throw new SecurityException("Only the assigned cashier can submit this shift.");
        }
        if (shift.status() != Status.OPEN) {
            throw new IllegalStateException("The shift has already been closed.");
        }
        return shift;
    }

    private static Variance calculateVariance(BigDecimal counted, BigDecimal expected) {
        var difference = counted.subtract(expected);
        if (difference.signum() == 0) {
            return new Variance(Difference.MATCH, BigDecimal.ZERO);
        }
        var result = difference.signum() < 0 ? Difference.SHORTAGE : Difference.SURPLUS;
        return new Variance(result, difference.abs());
    }
}
