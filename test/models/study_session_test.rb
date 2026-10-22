require "test_helper"

class StudySessionTest < ActiveSupport::TestCase
  test "calculates duration from the timestamps" do
    study_session = StudySession.new(
      client_session_id: "model-test-session",
      started_at: Time.zone.parse("2026-09-26 14:00:00"),
      ended_at: Time.zone.parse("2026-09-26 14:45:00")
    )

    assert study_session.valid?
    assert_equal 2_700, study_session.duration_seconds
  end

  test "rejects an end time before the start time" do
    study_session = StudySession.new(
      client_session_id: "backwards-session",
      started_at: Time.zone.parse("2026-09-26 14:45:00"),
      ended_at: Time.zone.parse("2026-09-26 14:00:00")
    )

    assert_not study_session.valid?
    assert_includes study_session.errors[:ended_at],
                    "must be greater than or equal to started_at"
  end
end
