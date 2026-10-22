require "test_helper"

class VerticalSliceTest < ActionDispatch::IntegrationTest
  test "creates, retries, and displays a completed session" do
    payload = {
      client_session_id: "vertical-slice-session",
      started_at: "2026-09-26T14:00:00Z",
      ended_at: "2026-09-26T14:45:00Z",
      subject: "Vertical Slice Subject",
      chapter: "Rails basics",
      notes: "Created by the vertical slice test",
      metadata: { focus_mode: true }
    }

    assert_difference("StudySession.count", 1) do
      post "/sessions", params: payload, as: :json
    end

    assert_response :created
    created_session = response.parsed_body
    assert_equal 2_700, created_session["duration_seconds"]

    assert_no_difference("StudySession.count") do
      post "/sessions", params: payload, as: :json
    end

    assert_response :ok
    assert_equal created_session["id"], response.parsed_body["id"]

    get "/dashboard"

    assert_response :success
    assert_select "h1", "Study dashboard"
    assert_select "td", text: "Vertical Slice Subject"
  end
end
