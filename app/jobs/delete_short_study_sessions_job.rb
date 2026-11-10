class DeleteShortStudySessionsJob < ApplicationJob
  def perform
    StudySession.where("duration_seconds < ?", 60).delete_all
  end
end
