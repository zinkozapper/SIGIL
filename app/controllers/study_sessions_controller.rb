class StudySessionsController < ApplicationController
  # The Android client cannot provide a browser session's CSRF token. This is
  # a JSON API endpoint, so it uses its own transport-level boundary instead.
  skip_forgery_protection only: :create

  def create
    study_session = StudySession.find_or_initialize_by(
      client_session_id: session_params[:client_session_id]
    )

    if study_session.persisted?
      render json: study_session, status: :ok
    elsif study_session.save
      render json: study_session, status: :created
    else
      render json: { errors: study_session.errors.to_hash },
             status: :unprocessable_entity
    end
  rescue ActiveRecord::RecordNotUnique
    # The unique database index wins if two retries arrive at the same time.
    existing_session = StudySession.find_by!(
      client_session_id: session_params[:client_session_id]
    )

    render json: existing_session, status: :ok
  end

  private

  def session_params
    params.permit(
      :client_session_id,
      :started_at,
      :ended_at,
      :subject,
      :chapter,
      :notes,
      metadata: {}
    )
  end
end
