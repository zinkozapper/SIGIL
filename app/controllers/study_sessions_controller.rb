class StudySessionsController < ApplicationController
  # The Android client cannot provide a browser session's CSRF token. This is
  # a JSON API endpoint, so it uses its own transport-level boundary instead.
  skip_forgery_protection only: :create

  def create
    attributes = session_params
    study_session = StudySession.find_or_initialize_by(
      client_session_id: attributes[:client_session_id]
    )

    if study_session.persisted?
      Rails.logger.info("SIGIL session duplicate request_id=#{request.request_id} client_session_id=#{study_session.client_session_id}")
      render json: study_session, status: :ok
    else
      study_session.assign_attributes(attributes)

      if study_session.save
        Rails.logger.info("SIGIL session created request_id=#{request.request_id} client_session_id=#{study_session.client_session_id}")
        render json: study_session, status: :created
      else
        Rails.logger.warn("SIGIL session invalid request_id=#{request.request_id} error_fields=#{study_session.errors.attribute_names.uniq.join(',')}")
        render json: { errors: study_session.errors.to_hash },
               status: :unprocessable_entity
      end
    end
  rescue ActiveRecord::RecordNotUnique
    # The unique database index wins if two retries arrive at the same time.
    existing_session = StudySession.find_by!(
      client_session_id: session_params[:client_session_id]
    )

    render json: existing_session, status: :ok
  rescue ActionDispatch::Http::Parameters::ParseError => error
    Rails.logger.error("SIGIL malformed JSON request_id=#{request.request_id} error=#{error.class}")
    render json: { error: "Malformed JSON request" }, status: :bad_request
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
