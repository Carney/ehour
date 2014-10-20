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

package net.rrm.ehour.ui.timesheet.panel;

import com.google.common.collect.Lists;
import com.richemont.jira.JiraService;
import com.richemont.windchill.WindChillUpdateService;
import net.rrm.ehour.activity.status.ActivityStatus;
import net.rrm.ehour.approvalstatus.service.ApprovalStatusService;
import net.rrm.ehour.data.DateRange;
import net.rrm.ehour.domain.*;
import net.rrm.ehour.timesheet.dto.WeekOverview;
import net.rrm.ehour.timesheet.service.IOverviewTimesheet;
import net.rrm.ehour.timesheet.service.IPersistTimesheet;
import net.rrm.ehour.ui.common.BaseSpringWebAppTester;
import net.rrm.ehour.ui.common.session.EhourWebSession;
import net.rrm.ehour.ui.timesheet.dto.Timesheet;
import net.rrm.ehour.userpref.UserPreferenceService;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.util.tester.FormTester;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TimesheetPanelTest extends BaseSpringWebAppTester {
    private static final String TIMESHEET_PATH = "panel:timesheetFrame:greyFrame:timesheetFrame_body:timesheetForm";
    private static final String DAY1_PATH = "blueFrame:blueFrame_body:projects:0:rows:0:day1";
    private static final String DAY1_FULL_PATH = TIMESHEET_PATH + ":" + DAY1_PATH;
    private static final User USER = new User(1, "thies");

    @Mock
    private IPersistTimesheet persistTimesheet;

    @Mock
    private IOverviewTimesheet overviewTimesheet;

    @Mock
    private ApprovalStatusService approvalStatusService;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private WindChillUpdateService windChillUpdateService;

    @Mock
    private JiraService jiraService;


    @Before
    public void setup() throws Exception {
        getConfig().setCompleteDayHours(8l);
        EhourWebSession.getSession().reloadConfig();

        getMockContext().putBean(persistTimesheet);
        getMockContext().putBean(overviewTimesheet);
        getMockContext().putBean(approvalStatusService);
        getMockContext().putBean(userPreferenceService);
        getMockContext().putBean(windChillUpdateService);
        getMockContext().putBean(jiraService);

        when(jiraService.updateJiraIssues(isA(User.class), isA(List.class))).thenReturn(new ArrayList<String>());
    }

    @Test
    @Ignore
    // TODO what's wrong with this one?
    public void addDayComment() {
        startAndReplayWithDefaultWeekOverview();

        final String comment = "commentaar";

        ModalWindow window = openCommentWindow(DAY1_FULL_PATH);
        assertTrue(window.isShown());

        FormTester timesheetFormTester = tester.newFormTester(TIMESHEET_PATH);

        tester.debugComponentTrees();

        timesheetFormTester.setValue(DAY1_PATH + ":dayWin:content:comment", comment);
        String componentPath = DAY1_FULL_PATH + ":dayWin:content:comment";

        System.out.println(tester.getComponentFromLastRenderedPage(componentPath).getDefaultModelObjectAsString());
        tester.executeAjaxEvent(componentPath, "onchange");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":dayWin:content:submit", "onclick");

        Timesheet timesheet = (Timesheet) tester.getComponentFromLastRenderedPage("panel").getDefaultModelObject();
        assertEquals(comment, timesheet.getTimesheetEntries().get(0).getComment());

        tester.assertNoErrorMessage();
    }

    private ModalWindow openCommentWindow(String path) {
        ModalWindow window = (ModalWindow) tester.getComponentFromLastRenderedPage(path + ":dayWin");
        tester.executeAjaxEvent(path + ":commentLink", "onclick");
        return window;
    }

    @Test
    public void addDayCommentCancelled() {
        startAndReplayWithDefaultWeekOverview();

        final String comment = "commentaar";

        tester.debugComponentTrees();

        clickDay1();

        FormTester formTester = tester.newFormTester(TIMESHEET_PATH);
        formTester.setValue(DAY1_PATH + ":dayWin:content:comment", comment);

        tester.executeAjaxEvent(DAY1_FULL_PATH + ":dayWin:content:comment", "onchange");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":dayWin:content:cancel", "onclick");

        Timesheet timesheet = (Timesheet) tester.getComponentFromLastRenderedPage("panel").getDefaultModelObject();
        assertNull(timesheet.getTimesheetEntries().get(0).getComment());

        tester.assertNoErrorMessage();
    }

    private void clickDay1() {
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":commentLink", "onclick");
    }

    @Test
    public void updateCounts() {
        startAndReplayWithDefaultWeekOverview();

        FormTester formTester = tester.newFormTester(TIMESHEET_PATH);

        formTester.setValue(DAY1_PATH + ":day", "12");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":day", "onchange");
        tester.assertNoErrorMessage();
        tester.assertContains(DAY1_PATH + ":day");

        Label grandTotalLabel = (Label) tester.getComponentFromLastRenderedPage(TIMESHEET_PATH + ":blueFrame:blueFrame_body:grandTotal");
        assertEquals(12f, (Float) grandTotalLabel.getDefaultModelObject(), 0.01f);
    }

    @Test
    public void moveToNextWeek() {
        startAndReplayWithDefaultWeekOverview();

        tester.executeAjaxEvent("panel:timesheetFrame:greyFrame:title:nextWeek", "onclick");

        Calendar cal = getWebApp().getSession().getNavCalendar();

        Calendar now = GregorianCalendar.getInstance();
        now.add(Calendar.DAY_OF_YEAR, 1);

        assertTrue(now.getTime().before(cal.getTime()));
    }

    @Test
    public void shouldNotResendUnmodifiedEntries() {
        startAndReplayWithDefaultWeekOverview();

        FormTester formTester = tester.newFormTester(TIMESHEET_PATH);

        formTester.setValue(DAY1_PATH + ":day", "12");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":day", "onblur");
        tester.assertNoErrorMessage();
        tester.assertContains(DAY1_PATH + ":day");

        //changing another field should not resend the unmodified day1
        formTester.setValue("blueFrame:blueFrame_body:projects:0:rows:0:day2:day", "8");
        tester.executeAjaxEvent(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day2:day", "onblur");
        tester.assertNoErrorMessage();
        tester.assertContains("blueFrame:blueFrame_body:projects:0:rows:0:day2:day");
    }

    @Test
    public void shouldResetErrorState() {
        startAndReplayWithDefaultWeekOverview();

        FormTester formTester = tester.newFormTester(TIMESHEET_PATH);

        formTester.setValue(DAY1_PATH + ":day", "12");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":day", "onchange");
        tester.assertNoErrorMessage();
        tester.assertContains(DAY1_PATH + ":day");
        tester.assertContainsNot("color: #ff0000");

        formTester.setValue(DAY1_PATH + ":day", "ff");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":day", "onchange");
        tester.assertContains(DAY1_PATH + ":day");
        tester.assertContains("color: #ff0000");
        tester.assertErrorMessages("day.IConverter.Float");

        formTester.setValue(DAY1_PATH + ":day", "1");
        tester.executeAjaxEvent(DAY1_FULL_PATH + ":day", "onchange");
        tester.assertContains(DAY1_PATH + ":day");
        tester.assertContainsNot("color: #ff0000");
    }


    @SuppressWarnings("unchecked")
    @Test
    public void shouldSubmitSuccessful() {
        when(persistTimesheet.persistTimesheetWeek(any(Collection.class), any(TimesheetComment.class), any(DateRange.class)))
                .thenReturn(new ArrayList<ActivityStatus>());

        when(approvalStatusService.getApprovalStatusForUserWorkingForCustomer(isA(User.class), isA(Customer.class), isA(DateRange.class)))
                .thenReturn(new ArrayList<ApprovalStatus>());
        
        startAndReplayWithDefaultWeekOverview();

        tester.executeAjaxEvent(TIMESHEET_PATH + ":commentsFrame:commentsFrame_body:submitButton", "onclick");

        tester.assertNoErrorMessage();
    }

    @Test
    public void shouldDisableInputForLockedDays() {
        Date lockedDay = new LocalDate().plusDays(1).toDate();

        startWithLockedDays(Arrays.asList(lockedDay));

        tester.assertComponent(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day2:day", Label.class);
        tester.assertComponent(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day3:day", TimesheetTextField.class);

        tester.assertNoErrorMessage();
    }

    @Test
    public void shouldDanybleCommentInputForLockedDaysWithExistingComment() {
        // given
        Date lockedDay = new LocalDate().plusDays(1).toDate();
        List<Date> lockedDates = Arrays.asList(lockedDay);

        Calendar now = GregorianCalendar.getInstance();
        now.add(Calendar.DAY_OF_WEEK, 7);

        DateRange nextWeekRange = new DateRange(new Date(), now.getTime());

        TimesheetEntry timesheetEntry = TimesheetEntryObjectMother.createTimesheetEntry(1, lockedDay, 5);
        timesheetEntry.setComment("Comment");

        List<TimesheetEntry> entries = Arrays.asList(timesheetEntry);
        List<Activity> activities = Arrays.asList(ActivityMother.createActivity(1));

        WeekOverview overview = new WeekOverview(entries, null, activities, nextWeekRange, USER, lockedDates);

        whenDefaultWeekOverview(overview);
        expectApprovalStatus();

        // when
        tester.startComponentInPage(new TimesheetPanel("panel", USER, new GregorianCalendar()));

        // then
        openCommentWindow(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day2");

        tester.assertComponent(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day2:dayWin:content:comment", Label.class);

        tester.assertNoErrorMessage();
        tester.assertNoInfoMessage();
    }

    @Test
    public void shouldHideCommentInputLinkForLockedDaysWithoutComment() {
        Date lockedDay = new LocalDate().plusDays(1).toDate();

        startWithLockedDays(Arrays.asList(lockedDay));

        Component commentLink = tester.getComponentFromLastRenderedPage(TIMESHEET_PATH + ":blueFrame:blueFrame_body:projects:0:rows:0:day2:commentLink");
        assertNull(commentLink); // null = not visible...

        tester.assertNoErrorMessage();
    }

    @Test
    public void shouldAddLockedIconInDayForLockedDay() {
        Date lockedDay = new LocalDate().plusDays(1).toDate();

        startWithLockedDays(Arrays.asList(lockedDay));

        String path = TIMESHEET_PATH + ":blueFrame:blueFrame_body:day2Label:lock:lockedContainer";
        tester.assertVisible(path);

        tester.assertNoErrorMessage();
    }

    private void startAndReplayWithDefaultWeekOverview() {
        startWithLockedDays(Lists.<Date>newArrayList());
    }

    private void startWithLockedDays(List<Date> lockedDays) {
        whenDefaultWeekOverview(withDefaultWeekOverview(lockedDays));

        expectApprovalStatus();

        tester.startComponentInPage(new TimesheetPanel("panel", USER, new GregorianCalendar()));
    }


    private void expectApprovalStatus() {
        List<ApprovalStatus> approvalStatuses = new ArrayList<ApprovalStatus>();
        ApprovalStatus approvalStatus = new ApprovalStatus();
        approvalStatus.setStatus(ApprovalStatusType.IN_PROGRESS);
        approvalStatuses.add(approvalStatus);

        when(approvalStatusService.getApprovalStatusForUserWorkingForCustomer(isA(User.class), isA(Customer.class), isA(DateRange.class))).thenReturn(approvalStatuses);
    }

    private void whenDefaultWeekOverview(WeekOverview overview) {
        when(overviewTimesheet.getWeekOverview(any(User.class), any(Calendar.class))).thenReturn(overview);
    }

    private WeekOverview withDefaultWeekOverview(List<Date> lockedDates) {
        Calendar now = GregorianCalendar.getInstance();
        now.add(Calendar.DAY_OF_WEEK, 7);

        DateRange nextWeekRange = new DateRange(new Date(), now.getTime());

        List<TimesheetEntry> entries = Arrays.asList(TimesheetEntryObjectMother.createTimesheetEntry(1, new Date(), 5));
        List<Activity> activities = Arrays.asList(ActivityMother.createActivity(1));

        return new WeekOverview(entries, null, activities, nextWeekRange, USER, lockedDates);
    }
}
