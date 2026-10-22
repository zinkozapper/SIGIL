class DashboardController < ApplicationController
  def show
    @total_sessions = StudySession.count
    @total_duration_seconds = StudySession.sum(:duration_seconds)
    @average_duration_seconds = if @total_sessions.zero?
      0
    else
      (@total_duration_seconds.to_f / @total_sessions).round
    end

    @recent_sessions = StudySession.order(started_at: :desc).limit(20)

    start_date = 13.days.ago.to_date
    daily_scope = StudySession.where(started_at: start_date.beginning_of_day..)
    daily_duration_totals = daily_scope.group("date(started_at)").sum(:duration_seconds)
    daily_session_totals = daily_scope.group("date(started_at)").count

    @daily_activity = (start_date..Date.current).map do |date|
      key = date.to_s

      {
        date: date,
        duration_seconds: daily_duration_totals.fetch(key, 0),
        session_count: daily_session_totals.fetch(key, 0)
      }
    end

    @max_daily_duration_seconds = [
      @daily_activity.map { |day| day[:duration_seconds] }.max.to_i,
      1
    ].max
  end
end
