--
-- PostgreSQL database dump
--

\restrict 286XGNfTEG6bAatFcU7032iIMBu0LMDUb5ZBarficvWgRgLIPol4Xiwe8PfOxNE

-- Dumped from database version 18.1
-- Dumped by pg_dump version 18.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: postgres
--

INSERT INTO public.roles VALUES (1, 'ROLE_USER');
INSERT INTO public.roles VALUES (2, 'ROLE_SELLER');
INSERT INTO public.roles VALUES (3, 'ROLE_ADMIN');


--
-- Data for Name: user_role; Type: TABLE DATA; Schema: public; Owner: postgres
--

INSERT INTO public.user_role VALUES (1, 1);
INSERT INTO public.user_role VALUES (2, 2);
INSERT INTO public.user_role VALUES (3, 2);
INSERT INTO public.user_role VALUES (3, 3);
INSERT INTO public.user_role VALUES (3, 1);


--
-- Name: roles_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.roles_role_id_seq', 3, true);


--
-- PostgreSQL database dump complete
--

\unrestrict 286XGNfTEG6bAatFcU7032iIMBu0LMDUb5ZBarficvWgRgLIPol4Xiwe8PfOxNE

