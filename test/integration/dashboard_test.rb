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
end
