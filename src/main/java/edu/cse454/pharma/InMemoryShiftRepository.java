package edu.cse454.pharma;

import java.util.Objects;
import java.util.Optional;

import edu.cse454.pharma.ShiftHandoverService.Shift;
import edu.cse454.pharma.ShiftHandoverService.ShiftRepository;
import edu.cse454.pharma.ShiftHandoverService.Status;
import edu.cse454.pharma.ShiftHandoverService.TenderTotals;

public final class InMemoryShiftRepository implements ShiftRepository {
    private Shift shift;
    private final TenderTotals expected;
    private TenderTotals submittedCount;

    public InMemoryShiftRepository(Shift shift, TenderTotals expected) {
        this.shift = Objects.requireNonNull(shift);
        this.expected = Objects.requireNonNull(expected);
    }

    @Override
    public synchronized Optional<Shift> findById(String shiftId) {
        return shift.id().equals(shiftId) ? Optional.of(shift) : Optional.empty();
    }

    @Override
    public synchronized Optional<TenderTotals> closeIfOpenAndOwned(
            String shiftId, String authenticatedCashierId, TenderTotals counted) {
        if (!shift.id().equals(shiftId) || !shift.cashierId().equals(authenticatedCashierId)
                || shift.status() != Status.OPEN) {
            return Optional.empty();
        }

        submittedCount = Objects.requireNonNull(counted);
        shift = new Shift(shift.id(), shift.cashierId(), Status.CLOSED);
        return Optional.of(expected);
    }

    public synchronized Optional<TenderTotals> submittedCount() {
        return Optional.ofNullable(submittedCount);
    }
}
