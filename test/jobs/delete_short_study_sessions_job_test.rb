require "test_helper"

class DeleteShortStudySessionsJobTest < ActiveJob::TestCase
  test "deletes sessions shorter than one minute only" do
    started_at = Time.current
    short = StudySession.create!(client_session_id: "short", started_at: started_at, ended_at: started_at + 59.seconds)
    minute = StudySession.create!(client_session_id: "minute", started_at: started_at, ended_at: started_at + 60.seconds)

    DeleteShortStudySessionsJob.perform_now

    assert_not StudySession.exists?(short.id)
    assert StudySession.exists?(minute.id)
  end
end
