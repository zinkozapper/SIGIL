require "test_helper"

class DashboardTest < ActionDispatch::IntegrationTest
  test "shows study totals and recent sessions" do
    get "/dashboard"

    assert_response :success
    assert_select "h1", "Study dashboard"
    assert_select ".metric-card", 3
    assert_select ".sessions-table"
    assert_select "td", text: "New Testament"
  end

  test "filters and groups chart activity by selected range" do
    travel_to Time.zone.local(2026, 10, 1, 12) do
      get "/dashboard", params: { range: "6m" }

      assert_response :success
      assert_select 'a[aria-current="page"]', "6 months"
      assert_select ".chart-period", text: /7-day groups/
      assert_select ".chart-heading span", text: "1h 15m total"
      assert_select ".chart-heading span", text: "2 total"

      get "/dashboard", params: { range: "invalid" }
      assert_select 'a[aria-current="page"]', "2 weeks"
      assert_select ".chart-column", 28
    end
  end

  test "chart links show daily totals and selected sessions" do
    travel_to Time.zone.local(2026, 10, 1, 12) do
      get "/dashboard", params: { period: "2026-09-26" }

      assert_response :success
      assert_select ".chart-y-axis", 2
      assert_select '.chart-bar-track[title*="Sep 26, 2026: 0h 45m 0s"]', 2
      assert_select '.chart-bar-track[href*="period=2026-09-26"]', 2
      assert_select "#period-details h2", "Sessions on Sep 26, 2026"
      assert_select "#period-details td", text: "Fixture session one"
      assert_select "#period-details td", text: "Psalms", count: 0

      get "/dashboard", params: { period: "2026-09-25" }
      assert_select "#period-details", text: /No sessions in this period/

      get "/dashboard", params: { period: "invalid" }
      assert_select "#period-details", 0
    end
  end

  test "grouped chart bars show daily totals and all sessions in group" do
    travel_to Time.zone.local(2026, 10, 1, 12) do
      get "/dashboard", params: { range: "6m" }
      link = css_select('.chart-bar-track[title*="Sep 26, 2026: 0h 45m 0s"]').first
      assert_includes link["title"], "Sep 27, 2026: 0h 30m 0s"
      get link["href"]
      assert_select "#period-details td", text: "New Testament"
      assert_select "#period-details td", text: "Psalms"
    end
  end
end
