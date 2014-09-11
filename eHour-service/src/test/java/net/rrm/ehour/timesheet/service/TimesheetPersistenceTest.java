/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package net.rrm.ehour.timesheet.service;

import net.rrm.ehour.activity.status.ActivityStatus;
import net.rrm.ehour.activity.status.ActivityStatusService;
import net.rrm.ehour.approvalstatus.service.ApprovalStatusService;
import net.rrm.ehour.data.DateRange;
import net.rrm.ehour.domain.*;
import net.rrm.ehour.exception.OverBudgetException;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetCommentDao;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetDao;
import net.rrm.ehour.util.DateUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.easymock.EasyMock.*;

/**
 * @author thies
 */
public class TimesheetPersistenceTest {
    private TimesheetPersistance persister;
    private TimesheetDao timesheetDAO;
    private ActivityStatusService statusService;
    private Activity activity;
    private List<TimesheetEntry> newEntries;
    private List<TimesheetEntry> existingEntries;
    private ApprovalStatusService approvalStatusService;

    @Before
    public void setUp() {
        timesheetDAO = createMock(TimesheetDao.class);
        statusService = createMock(ActivityStatusService.class);
        ApplicationContext context = createMock(ApplicationContext.class);
        TimesheetCommentDao commentDao = createMock(TimesheetCommentDao.class);
        approvalStatusService = createMock(ApprovalStatusService.class);

        persister = new TimesheetPersistance(timesheetDAO, commentDao, statusService, context, approvalStatusService);

        initData();
    }

    @SuppressWarnings("deprecation") //new dates
    private void initData() {
        activity = ActivityMother.createActivity(1);
        activity.getProject().setProjectManager(UserObjectMother.createUser());

        newEntries = new ArrayList<TimesheetEntry>();

        Date dateA = new Date(2008 - 1900, 4 - 1, 1);
        Date dateB = new Date(2008 - 1900, 4 - 1, 2);

        {
            TimesheetEntry entry = new TimesheetEntry();
            TimesheetEntryId id = new TimesheetEntryId();
            id.setActivity(activity);
            id.setEntryDate(dateA);
            entry.setEntryId(id);
            entry.setHours(8f);
            newEntries.add(entry);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setActivity(activity);
            idDel.setEntryDate(dateB);
            entryDel.setEntryId(idDel);
            entryDel.setHours(null);
            newEntries.add(entryDel);
        }

        existingEntries = new ArrayList<TimesheetEntry>();
        {
            TimesheetEntry entry = new TimesheetEntry();
            TimesheetEntryId id = new TimesheetEntryId();
            id.setActivity(activity);
            id.setEntryDate(dateA);
            entry.setEntryId(id);
            entry.setHours(5f);
            existingEntries.add(entry);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setActivity(activity);
            idDel.setEntryDate(dateB);
            entryDel.setEntryId(idDel);
            entryDel.setHours(5f);
            existingEntries.add(entryDel);
        }
    }

    @Test
    public void shouldPersistValidatedTimesheetAndCreateAnApprovalStatusForFirstTime() throws OverBudgetException {
        DateRange dateRange = DateUtil.getDateRangeForMonth(new Date());

        timesheetDAO.delete(isA(TimesheetEntry.class));

        expect(timesheetDAO.merge(isA(TimesheetEntry.class))).andReturn(null);

        expect(timesheetDAO.getTimesheetEntriesInRange(activity, dateRange)).andReturn(existingEntries);

        expect(statusService.getActivityStatus(activity)).andReturn(new ActivityStatus()).times(2);

        expect(approvalStatusService.getApprovalStatusForUserWorkingForCustomer(isA(User.class), isA(Customer.class), isA(DateRange.class))).andReturn(null);

        approvalStatusService.persist(isA(ApprovalStatus.class));

        replay(statusService);
        replay(timesheetDAO);
        replay(approvalStatusService);

        persister.validateAndPersist(activity, newEntries, dateRange);

        verify(timesheetDAO);
        verify(statusService);
        verify(approvalStatusService);
    }

    @Test(expected = OverBudgetException.class)
    public void testPersistInvalidTimesheet() throws OverBudgetException {
        timesheetDAO.delete(isA(TimesheetEntry.class));

        expect(timesheetDAO.merge(isA(TimesheetEntry.class))).andReturn(null);

        expect(timesheetDAO.getTimesheetEntriesInRange(isA(Activity.class), isA(DateRange.class))).andReturn(existingEntries);

        expect(statusService.getActivityStatus(activity)).andReturn(new ActivityStatus());

        ActivityStatus inValidStatus = new ActivityStatus();
        inValidStatus.setValid(false);

        expect(statusService.getActivityStatus(activity)).andReturn(inValidStatus);

        replay(statusService);
        replay(timesheetDAO);

        persister.validateAndPersist(activity, newEntries, new DateRange());

        verify(timesheetDAO);
        verify(statusService);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testPersistOverrunDecreasingTimesheet() throws OverBudgetException
    {
        Date dateC = new Date(2008 - 1900, 4 - 1, 3);

        newEntries.clear();
        existingEntries.clear();

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setActivity(activity);
            idDel.setEntryDate(dateC);
            entryDel.setEntryId(idDel);
            entryDel.setHours(7f);
            newEntries.add(entryDel);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setActivity(activity);
            idDel.setEntryDate(dateC);
            entryDel.setEntryId(idDel);
            entryDel.setHours(8f);
            existingEntries.add(entryDel);
        }

        expect(timesheetDAO.merge(isA(TimesheetEntry.class))).andReturn(null);

        expect(timesheetDAO.getTimesheetEntriesInRange(isA(Activity.class), isA(DateRange.class))).andReturn(existingEntries);

        ActivityStatus inValidStatus = new ActivityStatus();
        inValidStatus.setValid(false);

        expect(statusService.getActivityStatus(activity)).andReturn(inValidStatus).times(2);
        expect(approvalStatusService.getApprovalStatusForUserWorkingForCustomer(isA(User.class), isA(Customer.class), isA(DateRange.class))).andReturn(new ArrayList<ApprovalStatus>()).anyTimes();
        approvalStatusService.persist(isA(ApprovalStatus.class));

        replay(statusService);
        replay(timesheetDAO);
        replay(approvalStatusService);

        DateRange weekRange = new DateRange();
        Date dateStart = new Date();
        weekRange.setDateStart(dateStart);
        weekRange.setDateEnd(dateStart);

        persister.validateAndPersist(activity, newEntries, weekRange);

        verify(timesheetDAO);
        verify(statusService);
        verify(approvalStatusService);
    }

    /**
     *
     * @throws OverBudgetException
     */
    @Test(expected=OverBudgetException.class)
    public void testPersistOverrunInvalidTimesheet() throws OverBudgetException
    {
        expect(timesheetDAO.getTimesheetEntriesInRange(isA(Activity.class), isA(DateRange.class))).andReturn(existingEntries);

        ActivityStatus inValidStatus = new ActivityStatus();
        inValidStatus.setValid(false);

        expect(statusService.getActivityStatus(activity)).andReturn(inValidStatus).times(2);

        replay(statusService);
        replay(timesheetDAO);

        persister.validateAndPersist(activity, newEntries, new DateRange());

        verify(timesheetDAO);
        verify(statusService);
    }
}

