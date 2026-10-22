module DashboardHelper
  def format_duration(total_seconds)
    total_seconds = total_seconds.to_i
    hours = total_seconds / 3_600
    minutes = (total_seconds % 3_600) / 60

    if hours.positive?
      "#{hours}h #{minutes}m"
    else
      "#{minutes}m"
    end
  end
end
