require "test_helper"

class StudySessionsTest < ActionDispatch::IntegrationTest
  setup do
    @payload = {
      client_session_id: "phone-generated-uuid",
      started_at: "2026-09-26T14:00:00Z",
      ended_at: "2026-09-26T14:45:00Z",
      subject: "New Testament",
      chapter: "Romans 8",
      notes: "Reviewed the chapter outline",
      metadata: {
        focus_mode: true,
        lighting_profile: "study"
      }
    }
  end

  test "creates a completed study session" do
    assert_difference("StudySession.count", 1) do
      post_session(@payload)
    end

    assert_response :created
    response_body = response.parsed_body

    assert_equal "phone-generated-uuid", response_body["client_session_id"]
    assert_equal 2_700, response_body["duration_seconds"]
    assert_equal @payload[:metadata].stringify_keys, response_body["metadata"]
  end

  test "returns the existing session for a duplicate client session ID" do
    post_session(@payload)
    created_id = response.parsed_body["id"]

    duplicate_payload = @payload.merge(notes: "This retry must not overwrite the original")

    assert_no_difference("StudySession.count") do
      post_session(duplicate_payload)
    end

    assert_response :ok
    assert_equal created_id, response.parsed_body["id"]
    assert_equal @payload[:notes], response.parsed_body["notes"]
  end

  test "returns unprocessable entity for an invalid payload" do
    invalid_payload = @payload.merge(
      client_session_id: "invalid-session",
      ended_at: "2026-09-26T13:59:59Z"
    )

    assert_no_difference("StudySession.count") do
      post_session(invalid_payload)
    end

    assert_response :unprocessable_entity
    assert response.parsed_body["errors"].key?("ended_at")
  end

  test "ignores fields outside the documented payload" do
    payload_with_unknown_field = @payload.merge(
      client_session_id: "unknown-field-session",
      status: "completed"
    )

    post_session(payload_with_unknown_field)

    assert_response :created
    assert_not StudySession.last.attributes.key?("status")
  end

  private

  def post_session(payload)
    post "/sessions", params: payload, as: :json
  end
end
