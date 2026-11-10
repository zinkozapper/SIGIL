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

    ranges = { "2w" => 13.days, "1m" => 1.month, "6m" => 6.months, "1y" => 1.year }
    @range = ranges.key?(params[:range]) ? params[:range] : "2w"
    start_date = Date.current - ranges.fetch(@range)
    daily_scope = StudySession.where(started_at: start_date.beginning_of_day..Date.current.end_of_day)
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

    bucket_size = { "2w" => 1, "1m" => 1, "6m" => 7, "1y" => 14 }.fetch(@range)
    @activity = @daily_activity.each_slice(bucket_size).map do |days|
      {
        start_date: days.first[:date],
        end_date: days.last[:date],
        duration_seconds: days.sum { |day| day[:duration_seconds] },
        session_count: days.sum { |day| day[:session_count] }
      }
    end

    @selected_period = @activity.find { |period| period[:start_date].iso8601 == params[:period] }
    if @selected_period
      @period_sessions = StudySession.where(
        started_at: @selected_period[:start_date].beginning_of_day..@selected_period[:end_date].end_of_day
      ).order(started_at: :desc)
    end
  end
end
