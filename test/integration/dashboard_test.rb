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
end
